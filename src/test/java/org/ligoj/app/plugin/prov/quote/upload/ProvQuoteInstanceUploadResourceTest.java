/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.upload;

import static org.ligoj.app.plugin.prov.quote.upload.ProvQuoteUploadResource.DEFAULT_ENCODING;
import static org.ligoj.app.plugin.prov.quote.upload.ProvQuoteUploadResource.DEFAULT_SEPARATOR;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;;

/**
 * Test class of {@link ProvQuoteInstanceUploadResource}
 */
class ProvQuoteInstanceUploadResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteUploadResource qiuResource;

	private Map<String, Floating> toStoragesFloating(final String instanceName) {
		return qsRepository.findAllBy("quoteInstance.name", instanceName).stream()
				.collect(Collectors.toMap(ProvQuoteStorage::getName, qs -> new Floating(qs.getCost(), qs.getMaxCost(),
						0, 0, qs.getQuoteInstance().getMaxQuantity() == null, qs.getCo2(), qs.getMaxCo2())));
	}

	@Test
	void upload() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload.csv").getInputStream(),
				new String[] { "\"name\"", "cpu", "gpu", "ram", "disk", "latency", "os", "constant", "description" },
				false, "Full Time 12 month", 1);
		checkUpload();
	}

	@Test
	void uploadNormalizedUsageName() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload.csv").getInputStream(),
				new String[] { "\"name\"", "cpu", "gpu", "ram", "disk", "latency", "os", "constant", "description" },
				false, "full time 12 MONTH", 1);
		checkUpload();
	}

	@Test
	void uploadCreateAsNeed() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload.csv").getInputStream(),
				new String[] { "\"name\"", "cpu", "gpu", "ram", "disk", "latency", "os", "constant", "description" },
				false, "Will be created", MergeMode.INSERT, 1, false, DEFAULT_ENCODING, true, DEFAULT_SEPARATOR);
		final var configuration = getConfiguration();
		Assertions.assertEquals(18, configuration.getInstances().size());
		checkCost(configuration.getCost(), 14649.926, 17099.526, false);
	}

	@Test
	void uploadIncludedHeaders() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-with-headers.csv").getInputStream(),
				null, true, "Full Time 12 month", 1);
		final var configuration = checkUpload();
		Assertions.assertEquals(10.1d, configuration.getInstances().get(0).getMaxVariableCost(), DELTA);
	}

	private QuoteVo checkUpload() {
		final var configuration = getConfiguration();
		Assertions.assertEquals(18, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assertions.assertEquals(15, configuration.getStorages().size());
		Assertions.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14584.046, 17033.646, false);

		Assertions.assertEquals("JIRA", configuration.getInstances().get(7).getName());
		Assertions.assertEquals("Description JIRA", configuration.getInstances().get(7).getDescription());
		return configuration;
	}

	@Test
	void uploadDefaultHeader() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-default.csv").getInputStream(), null,
				false, "Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(18, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assertions.assertEquals(1, configuration.getInstances().get(17).getMinQuantity());
		Assertions.assertEquals(1, configuration.getInstances().get(17).getMaxQuantity().intValue());
		Assertions.assertNull(configuration.getInstances().get(17).getMaxVariableCost());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(12).getPrice().getType().getName());
		Assertions.assertEquals(14, configuration.getStorages().size());
		Assertions.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14547.606, 16997.206, false);
	}

	@Test
	void uploadCoverageProgress() throws IOException {
		final var content = new StringBuilder();
		final var headers = "name;cpu;gpu;ram;disk;os";
		content.append(headers + "\n");
		for (var i = 0; i < 20; i++) {
			content.append("name").append(i).append(";1;0;1;1;LINUX\n");
		}
		qiuResource.upload(subscription, newStream(content.toString()), null, true, "Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(27, configuration.getInstances().size());
	}

	@Test
	void uploadFixedInstanceType() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;instance10;true"),
				new String[] { "name", "cpu", "ram", "os", "type", "ephemeral" }, false, "Full Time 12 month", 1);
		var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var term = configuration.getInstances().get(7).getPrice().getTerm();
		Assertions.assertEquals("1y", term.getName());
		Assertions.assertEquals("instance10", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 6344.438, 8794.038, false);

		// A refresh erases the instance type constraint
		em.flush();
		em.clear();
		resource.refresh(subscription);
		configuration = getConfiguration();
		Assertions.assertEquals("1y", term.getName());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		checkCost(configuration.getCost(), 3267.88, 5717.48, false);
	}

	@Test
	void uploadEmptyRow() throws IOException {
		Assertions.assertEquals(7, getConfiguration().getInstances().size());
		em.clear();
		qiuResource.upload(subscription, newStream(";;;;"), new String[] { "name", "cpu", "ram", "os" }, false,
				"Full Time 12 month", 1);
		var configuration = getConfiguration();
		checkCost(configuration.getCost(), 4704.758, 7154.358, false);
		Assertions.assertEquals(7, getConfiguration().getInstances().size());
	}

	@Test
	void uploadMax() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;0.2;2;4;500;300;LINUX;100;80"),
				new String[] { "name", "cpu", "cpu_max", "gpu", "gpu_max", "ram", "ram-MAX", "os", "disk", "disk_Max" },
				false, "Full Time 12 month", 1);
		var configuration = getConfiguration();
		checkCost(configuration.getCost(), 4945.358, 7394.958, false);
		// checkCost(configuration.getCost(), 4828.238, 7277.838, false);
		configuration = getConfiguration();
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals("ANY", qi.getName());
		Assertions.assertEquals("instance3", qi.getPrice().getType().getName());
		Assertions.assertEquals(0.5, qi.getCpu());
		Assertions.assertEquals(0.2, qi.getCpuMax());
		Assertions.assertEquals(2, qi.getGpu());
		Assertions.assertEquals(4, qi.getGpuMax());
		Assertions.assertEquals(500, qi.getRam());
		Assertions.assertEquals(300, qi.getRamMax());
		final var qs = configuration.getStorages().get(4);
		Assertions.assertEquals("ANY", qs.getName());
		Assertions.assertEquals(100, qs.getSize());
		Assertions.assertEquals(80, qs.getSizeMax());
	}

	@Test
	void uploadDatabase() throws IOException {
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, DEFAULT_ENCODING);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		Assertions.assertEquals(7, getConfiguration().getDatabases().size());
		em.clear();

		qiuResource.upload(subscription, newStream("ANY;0.5;500;MySQL"),
				new String[] { "name", "cpu", "ram", "engine" }, false, "Full Time 12 month", 1);
		var configuration = getConfiguration();
		checkCost(configuration.getCost(), 4794.258, 7243.858, false);
		configuration = getConfiguration();
		Assertions.assertEquals(7, configuration.getInstances().size());
		Assertions.assertEquals(8, configuration.getDatabases().size());
		final var qb = configuration.getDatabases().get(7);
		Assertions.assertEquals("database1", qb.getPrice().getType().getName());
		Assertions.assertEquals(0.5, qb.getCpu());
		Assertions.assertEquals(0, qb.getGpu());
		Assertions.assertEquals(500, qb.getRam());
		Assertions.assertEquals("MYSQL", qb.getEngine());
	}

	@Test
	void uploadDatabaseUpdate() throws IOException {
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, DEFAULT_ENCODING);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		Assertions.assertEquals(7, getConfiguration().getDatabases().size());
		Assertions.assertEquals("STANDARD ONE", getConfiguration().getDatabases().stream()
				.filter(q -> q.getName().equals("database4")).findFirst().get().getEdition());
		em.clear();

		qiuResource.upload(subscription,
				newStream("database4;0.5;1000;oracle;standard two\ndatabaseNEW;0.4;800;mysql;"),
				new String[] { "name", "cpu", "ram", "engine", "edition" }, false, "Full Time 12 month",
				MergeMode.UPDATE, 1, false, DEFAULT_ENCODING, false, DEFAULT_SEPARATOR);
		var configuration = getConfiguration();
		checkCost(configuration.getCost(), 4905.058, 7354.658, false);
		configuration = getConfiguration();
		Assertions.assertEquals(7, configuration.getInstances().size());
		Assertions.assertEquals(8, configuration.getDatabases().size());

		// Updated database
		final var qb = configuration.getDatabases().stream().filter(q -> q.getName().equals("database4")).findFirst()
				.get();
		Assertions.assertEquals("database1", qb.getPrice().getType().getName());
		Assertions.assertEquals(0.5, qb.getCpu());
		Assertions.assertEquals(1000, qb.getRam());
		Assertions.assertEquals("ORACLE", qb.getEngine());
		Assertions.assertEquals("STANDARD TWO", qb.getEdition());

		// New database
		final var qb2 = configuration.getDatabases().stream().filter(q -> q.getName().equals("databaseNEW")).findFirst()
				.get();
		Assertions.assertEquals("database1", qb2.getPrice().getType().getName());
		Assertions.assertEquals(0.4, qb2.getCpu());
		Assertions.assertEquals(800, qb2.getRam());
		Assertions.assertEquals("MYSQL", qb2.getEngine());
	}

	@Test
	void uploadBoundQuantities() throws IOException {
		qiuResource.upload(
				subscription, newStream("ANY;0.5;500;LINUX;1;true;1;1000;true"), new String[] { "name", "cpu", "ram",
						"os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7); // The last one
		Assertions.assertEquals("ANY", qi.getName());
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals("1y", qi.getPrice().getTerm().getName());
		Assertions.assertFalse(qi.getPrice().getTerm().isVariable());
		Assertions.assertEquals(1000, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 117164.358, false);
		final var storagesFloating = toStoragesFloating("ANY");
		Assertions.assertEquals(1, storagesFloating.size());
		checkCost(storagesFloating.values().iterator().next(), 0.21, 210, false);
	}

	@Test
	void uploadMaxQuantities() throws IOException {
		qiuResource.upload(
				subscription, newStream("ANY;0.5;500;LINUX;1;true;1;1;true"), new String[] { "name", "cpu", "ram", "os",
						"disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, false);
		final var storagesFloating = toStoragesFloating("ANY");
		Assertions.assertEquals(1, storagesFloating.size());
		checkCost(storagesFloating.values().iterator().next(), 0.21, 0.21, false);
	}

	@Test
	void uploadMultipleDisks() throws IOException {
		qiuResource.upload(subscription, newStream("MYINSTANCE;0.5;500;LINUX;1,0,10;true;true"),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "ephemeral" }, false,
				"Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(6, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4816.868, 7266.468, false);
		final var storagesFloating = toStoragesFloating("MYINSTANCE");
		Assertions.assertEquals(2, storagesFloating.size()); // 1GB and 10GB disks
		checkCost(storagesFloating.get("MYINSTANCE"), 0.21, 0.21, false);
		Assertions.assertEquals("MYINSTANCE", qsRepository.findAllBy("cost", .21d).get(0).getName());
		checkCost(storagesFloating.get("MYINSTANCE2"), 2.1, 2.1, false);
		Assertions.assertEquals("MYINSTANCE2", qsRepository.findAllBy("cost", 2.1d).get(0).getName());
	}

	@Test
	void uploadUnBoundQuantities() throws IOException {
		qiuResource.upload(
				subscription, newStream("ANY;0.5;500;LINUX;1;true;1;0;true"), new String[] { "name", "cpu", "ram", "os",
						"disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertNull(qi.getMaxQuantity());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, true);
		final var storagesFloating = toStoragesFloating("ANY");
		Assertions.assertEquals(1, storagesFloating.size());
		checkCost(storagesFloating.values().iterator().next(), 0.21, 0.21, true);
	}

	@Test
	void uploadInternetAccess() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;instance10;PUBLIC;true"),
				new String[] { "name", "cpu", "ram", "os", "type", "internet", "ephemeral" }, false,
				"Full Time 12 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
	}

	@Test
	void uploadDefaultUsage() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX"), new String[] { "name", "cpu", "ram", "os" },
				false, null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	void uploadRate() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;LOW;BEST;BEST;MEDIUM;WORST"), new String[] {
				"name", "cpu", "ram", "os", "cpuRate", "gpuRate", "ramRate", "networkrate", "storageRate" }, false,
				null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals("instance4", qi.getPrice().getType().getName());
		Assertions.assertEquals(Rate.LOW, qi.getCpuRate());
		Assertions.assertEquals(Rate.BEST, qi.getGpuRate());
		Assertions.assertEquals(Rate.BEST, qi.getRamRate());
		Assertions.assertEquals(Rate.MEDIUM, qi.getNetworkRate());
		Assertions.assertEquals(Rate.WORST, qi.getStorageRate());
		checkCost(configuration.getCost(), 4975.598, 7425.198, false);
	}

	@Test
	void uploadProcessor() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;Intel Xeon"),
				new String[] { "name", "cpu", "ram", "os", "processor" }, false, null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance11", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals("Intel Xeon", configuration.getInstances().get(7).getProcessor());
		checkCost(configuration.getCost(), 9389.558, 11839.158, false);
	}

	@Test
	void uploadTags() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;app:TAG1,app:TAG2 seç+-=._/@#&;8"),
				new String[] { "name", "cpu", "ram", "os", "tags", "disk" }, false, null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		final var id = configuration.getInstances().get(7).getId();
		var tags = configuration.getTags().get(ResourceType.INSTANCE).get(id);
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG1".equals(t.getValue())));
		Assertions.assertTrue(
				tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG2 seç+-=._/@#&".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().noneMatch(t -> "seç+-=._/@#&;8".equals(t.getName())));

		var sid = configuration.getStorages().stream().filter(s -> id.equals(s.getInstance())).findFirst().get()
				.getId();
		tags = configuration.getTags().get(ResourceType.STORAGE).get(sid);
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG1".equals(t.getValue())));
		Assertions.assertTrue(
				tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG2 seç+-=._/@#&".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().noneMatch(t -> "seç+-=._/@#&".equals(t.getName())));
	}

	@Test
	void uploadTagsInvalidTagName() {
		final var input = newStream("ANY;0.5;500;LINUX;app:!!;8");
		Assertions.assertThrows(ValidationJsonException.class, () -> qiuResource.upload(subscription, input,
				new String[] { "name", "cpu", "ram", "os", "tags", "disk" }, false, null, 1));
	}

	@Test
	void uploadTagsInvalidTagNameContinue() throws IOException {
		final var input = newStream("ANY;0.5;500;LINUX;app:!!;8");
		qiuResource.upload(subscription, input, new String[] { "name", "cpu", "ram", "os", "tags", "disk" }, false,
				null, MergeMode.UPDATE, 1, true, DEFAULT_ENCODING, false, DEFAULT_SEPARATOR);
	}

	@Test
	void uploadUpdate() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX\nANY 1;1;2000;LINUX\nANY;2;1000;LINUX"),
				new String[] { "name", "cpu", "ram", "os" }, false, null, MergeMode.UPDATE, 1, false, DEFAULT_ENCODING,
				false, DEFAULT_SEPARATOR);
		final var configuration = getConfiguration();
		Assertions.assertEquals(9, configuration.getInstances().size());
		Assertions.assertEquals("ANY 1", configuration.getInstances().get(7).getName());
		Assertions.assertEquals(1D, configuration.getInstances().get(7).getCpu());
		Assertions.assertEquals("ANY", configuration.getInstances().get(8).getName());
		Assertions.assertEquals(2D, configuration.getInstances().get(8).getCpu());
	}

	@Test
	void uploadNoConflictName() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX\nANY 1;1;2000;LINUX\nANY;2;1000;LINUX"),
				new String[] { "name", "cpu", "ram", "os" }, false, null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(10, configuration.getInstances().size());
		Assertions.assertEquals("ANY", configuration.getInstances().get(7).getName());
		Assertions.assertEquals(.5D, configuration.getInstances().get(7).getCpu());
		Assertions.assertEquals("ANY 1", configuration.getInstances().get(8).getName());
		Assertions.assertEquals(1D, configuration.getInstances().get(8).getCpu());
		Assertions.assertEquals("ANY 2", configuration.getInstances().get(9).getName());
		Assertions.assertEquals(2D, configuration.getInstances().get(9).getCpu());
	}

	@Test
	void uploadConflictName() {
		final var input = newStream("ANY;0.5;500;LINUX\nANY;2;1000;LINUX");
		Assertions.assertThrows(DataIntegrityViolationException.class,
				() -> qiuResource.upload(subscription, input, new String[] { "name", "cpu", "ram", "os" }, false, null,
						MergeMode.INSERT, 1, false, DEFAULT_ENCODING, false, DEFAULT_SEPARATOR));
	}

	@Test
	void uploadConflictNameContinue() throws IOException {
		final var input = newStream("ANY;0.5;500;LINUX\nANY;2;1000;LINUX");
		qiuResource.upload(subscription, input, new String[] { "name", "cpu", "ram", "os" }, false, null,
				MergeMode.INSERT, 1, true, DEFAULT_ENCODING, false, DEFAULT_SEPARATOR);
	}

	@Test
	void uploadUsagePerEntry() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX;Full Time 12 month"),
				new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 13 month", 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals("1y", configuration.getInstances().get(7).getPrice().getTerm().getName());
		checkCost(configuration.getCost(), 4807.238, 7256.838, false);
	}

	@Test
	void uploadOnlyCustomFound() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;999;6;LINUX"), null, false, "Full Time 12 month", 1024);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 247315.131, 249764.731, false);
	}

	@Test
	void uploadCustomLowest() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;1;64;LINUX"), null, false, "Full Time 12 month", 1024);
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 5155.878, 7605.478, false);
	}

	/**
	 * Expected usage does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	void uploadInvalidUsageForSubscription() {
		final var input = newStream("ANY;0.5;500;LINUX;Full Time2");
		Assertions.assertEquals("Full Time2", Assertions
				.assertThrows(EntityNotFoundException.class,
						() -> qiuResource.upload(subscription, input,
								new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1))
				.getMessage());
	}

	/**
	 * Expected location does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	void uploadInvalidLocationForSubscription() {
		final var input = newStream("ANY;0.5;500;LINUX;region-3");
		Assertions.assertEquals("region-3",
				Assertions.assertThrows(EntityNotFoundException.class, () -> qiuResource.upload(subscription, input,
						new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1))
						.getMessage());
	}

	/**
	 * Expected location does not exist at all?
	 */
	@Test
	void uploadInvalidLocation() {
		final var input = newStream("ANY;0.5;500;LINUX;region-ZZ");
		Assertions.assertEquals("region-ZZ",
				Assertions.assertThrows(EntityNotFoundException.class, () -> qiuResource.upload(subscription, input,
						new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1))
						.getMessage());
	}

	/**
	 * Expected usage does not exist at all.
	 */
	@Test
	void uploadInvalidUsage() {
		final var input = newStream("ANY;0.5;500;LINUX;any");
		Assertions.assertEquals("any", Assertions
				.assertThrows(EntityNotFoundException.class,
						() -> qiuResource.upload(subscription, input,
								new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1))
				.getMessage());
	}

	@Test
	void uploadInstanceNotFound() {
		final var input = newStream("ANY;999;6;WINDOWS");
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, input, null, false, "Full Time 12 month", 1024)),
				"csv-file.instance", "no-match-instance");
	}

	@Test
	void uploadInstanceNotFoundContinue() throws IOException {
		final var input = newStream("ANY;999;6;WINDOWS");
		qiuResource.upload(subscription, input, null, false, null, MergeMode.INSERT, 1, true, DEFAULT_ENCODING, false,
				DEFAULT_SEPARATOR);
	}

	@Test
	void uploadStorageNotFound() {
		final var input = newStream("ANY;1;1;LINUX;99999999999;BEST;THROUGHPUT");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> qiuResource.upload(subscription, input,
						new String[] { "name", "cpu", "ram", "os", "disk", "latency", "optimized" }, false,
						"Full Time 12 month", 1)),
				"csv-file.storage", "NotNull");
	}

	@Test
	void uploadMissingRequiredHeader() {
		final var input = newStream("ANY");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> qiuResource.upload(subscription, input, new String[] { "any" }, false, "Full Time 12 month", 1)),
				"csv-file", "missing-header");
	}

	@Test
	void uploadAmbiguousHeader() {
		final var input = newStream("ANY;ANY");
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class, () -> qiuResource.upload(subscription, input,
						new String[] { "vcpu", "core" }, false, "Full Time 12 month", 1)),
				"csv-file", "ambiguous-header");
	}

	@Test
	void uploadIgnoredInvalidHeader() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;ignored value1;0.5;500;any-value2;LINUX"),
				new String[] { "name", "ignore", "cpu", "ram", "ignore", "os" }, false, null, 1);
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	void uploadAlternativeHeader() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX"),
				new String[] { "name", "vCPU", "memory", "system" }, false, null, 1);
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	void uploadWildcardHeader() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;LINUX"),
				new String[] { "instance_name", "cpu #", "instance ram (GB)", " os " }, false, null, 1);
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	void uploadPriorizedHeader() throws IOException {
		qiuResource.upload(
				subscription, newStream("real name;alt. name;2,4;0.5;500;info;LINUX"), new String[] { "\" name  \"",
						"instance_name", "frequency cpu", "cpus", "instance ram (GB)", "   os(1)", "\"os\"" },
				false, null, 1);
		final var configuration = getConfiguration();
		Assertions.assertEquals("real name", configuration.getInstances().get(7).getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	void uploadSoftware() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;WINDOWS;SQL WEB"),
				new String[] { "name", "cpu", "ram", "os", "software" }, false, "Full Time 12 month", 1);
		var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C121", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("SQL WEB", configuration.getInstances().get(7).getPrice().getSoftware());
	}

	@Test
	void uploadLicense() throws IOException {
		qiuResource.upload(subscription, newStream("ANY;0.5;500;WINDOWS;BYOL"),
				new String[] { "name", "cpu", "ram", "os", "license" }, false, "Full Time 12 month", 1);
		var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C120", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("BYOL", configuration.getInstances().get(7).getPrice().getLicense());
	}

	private InputStream newStream(final String input) {
		return IOUtils.toInputStream(input, DEFAULT_ENCODING);
	}
}
