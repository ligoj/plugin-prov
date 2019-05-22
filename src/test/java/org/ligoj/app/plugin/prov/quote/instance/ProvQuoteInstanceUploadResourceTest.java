/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Test class of {@link ProvQuoteInstanceUploadResource}
 */
public class ProvQuoteInstanceUploadResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteInstanceUploadResource qiuResource;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	private Map<String, FloatingCost> toStoragesFloatingCost(final String instanceName) {
		return qsRepository.findAllBy("quoteInstance.name", instanceName).stream().collect(Collectors.toMap(
				ProvQuoteStorage::getName,
				qs -> new FloatingCost(qs.getCost(), qs.getMaxCost(), qs.getQuoteInstance().getMaxQuantity() == null)));
	}

	@Test
	public void upload() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload.csv").getInputStream(),
				new String[] { "\"name\"", "cpu", "ram", "disk", "latency", "os", "constant", "description" }, false,
				"Full Time 12 month", 1, "UTF-8");
		checkUpload();
	}

	@Test
	public void uploadIncludedHeaders() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-with-headers.csv").getInputStream(),
				null, true, "Full Time 12 month", 1, "UTF-8");
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
	public void uploadDefaultHeader() throws IOException {
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-default.csv").getInputStream(), null,
				false, "Full Time 12 month", 1, "UTF-8");
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
	public void uploadCoverageProgress() throws IOException {
		final var content = new StringBuilder();
		final var headers = "name;cpu;ram;disk;os";
		content.append(headers + "\n");
		for (var i = 0; i < 20; i++) {
			content.append("name").append(i).append(";1;1;1;LINUX\n");
		}
		qiuResource.upload(subscription, new StringInputStream(content.toString(), "UTF-8"), null, true,
				"Full Time 12 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(27, configuration.getInstances().size());
	}

	@Test
	public void uploadFixedInstanceType() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;instance10;true", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "type", "ephemeral" }, false, "Full Time 12 month", 1,
				"UTF-8");
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
	public void uploadBoundQuantities() throws IOException {
		qiuResource.upload(
				subscription, new StringInputStream("ANY;0.5;500;LINUX;1;true;1;1000;true", "UTF-8"), new String[] {
						"name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
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
		final var storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 210, false);
	}

	private QuoteVo getConfiguration() {
		em.flush();
		em.clear();
		return resource.getConfiguration(subscription);
	}

	@Test
	public void uploadMaxQuantities() throws IOException {
		qiuResource.upload(
				subscription, new StringInputStream("ANY;0.5;500;LINUX;1;true;1;1;true", "UTF-8"), new String[] {
						"name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, false);
		final var storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, false);
	}

	@Test
	public void uploadMultipleDisks() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("MYINSTANCE;0.5;500;LINUX;1,0,10;true;true", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "ephemeral" }, false,
				"Full Time 12 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(6, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4816.868, 7266.468, false);
		final var storagesFloatingCost = toStoragesFloatingCost("MYINSTANCE");
		Assertions.assertEquals(2, storagesFloatingCost.size()); // 1GB and 10GB disks
		checkCost(storagesFloatingCost.get("MYINSTANCE"), 0.21, 0.21, false);
		Assertions.assertEquals("MYINSTANCE", qsRepository.findAllBy("cost", .21d).get(0).getName());
		checkCost(storagesFloatingCost.get("MYINSTANCE2"), 2.1, 2.1, false);
		Assertions.assertEquals("MYINSTANCE2", qsRepository.findAllBy("cost", 2.1d).get(0).getName());
	}

	@Test
	public void uploadUnBoundQuantities() throws IOException {
		qiuResource.upload(
				subscription, new StringInputStream("ANY;0.5;500;LINUX;1;true;1;0;true", "UTF-8"), new String[] {
						"name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final var qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertNull(qi.getMaxQuantity());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, true);
		final var storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, true);
	}

	@Test
	public void uploadInternetAccess() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;instance10;PUBLIC;true", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "type", "internet", "ephemeral" }, false,
				"Full Time 12 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
	}

	@Test
	public void uploadDefaultUsage() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os" }, false, null, 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadTags() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;app:TAG1,app:TAG2 sec;8", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "tags", "disk" }, false, null, 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		final var id = configuration.getInstances().get(7).getId();
		var tags = configuration.getTags().get(ResourceType.INSTANCE).get(id);
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG1".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG2 sec".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().noneMatch(t -> "sec".equals(t.getName())));

		var sid = configuration.getStorages().stream().filter(s -> id.equals(s.getInstance())).findFirst().get()
				.getId();
		tags = configuration.getTags().get(ResourceType.STORAGE).get(sid);
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG1".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().anyMatch(t -> "app".equals(t.getName()) && "TAG2 sec".equals(t.getValue())));
		Assertions.assertTrue(tags.stream().noneMatch(t -> "sec".equals(t.getName())));
	}

	@Test
	public void uploadUpdate() throws IOException {
		qiuResource.upload(subscription,
				new StringInputStream("ANY;0.5;500;LINUX\nANY 1;1;2000;LINUX\nANY;2;1000;LINUX", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os" }, false, null, MergeMode.UPDATE, 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(9, configuration.getInstances().size());
		Assertions.assertEquals("ANY 1", configuration.getInstances().get(7).getName());
		Assertions.assertEquals(1D, configuration.getInstances().get(7).getCpu());
		Assertions.assertEquals("ANY", configuration.getInstances().get(8).getName());
		Assertions.assertEquals(2D, configuration.getInstances().get(8).getCpu());
	}

	@Test
	public void uploadNoConflictName() throws IOException {
		qiuResource.upload(subscription,
				new StringInputStream("ANY;0.5;500;LINUX\nANY 1;1;2000;LINUX\nANY;2;1000;LINUX", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os" }, false, null, 1, "UTF-8");
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
	public void uploadConflictName() throws IOException {
		Assertions.assertThrows(DataIntegrityViolationException.class,
				() -> qiuResource.upload(subscription,
						new StringInputStream("ANY;0.5;500;LINUX\nANY;2;1000;LINUX", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os" }, false, null, MergeMode.INSERT, 1, "UTF-8"));
	}

	@Test
	public void uploadUsagePerEntry() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;Full Time 12 month", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 13 month", 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals("1y", configuration.getInstances().get(7).getPrice().getTerm().getName());
		checkCost(configuration.getCost(), 4807.238, 7256.838, false);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;999;6;LINUX", "UTF-8"), null, false,
				"Full Time 12 month", 1024, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 247315.131, 249764.731, false);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;1;64;LINUX", "UTF-8"), null, false,
				"Full Time 12 month", 1024, "UTF-8");
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
	public void uploadInvalidUsageForSubscription() {
		Assertions.assertEquals("Full Time2", Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;Full Time2", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1, "UTF-8"))
				.getMessage());
	}

	/**
	 * Expected location does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	public void uploadInvalidLocationForSubscription() {
		Assertions.assertEquals("region-3", Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;region-3", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1,
						"UTF-8"))
				.getMessage());
	}

	/**
	 * Expected location does not exist at all?
	 */
	@Test
	public void uploadInvalidLocation() {
		Assertions.assertEquals("region-ZZ", Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;region-ZZ", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1,
						"UTF-8"))
				.getMessage());
	}

	/**
	 * Expected usage does not exist at all.
	 */
	@Test
	public void uploadInvalidUsage() {
		Assertions.assertEquals("any", Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX;any", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1, "UTF-8"))
				.getMessage());
	}

	@Test
	public void uploadInstanceNotFound() {
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, new StringInputStream("ANY;999;6;WINDOWS", "UTF-8"),
								null, false, "Full Time 12 month", 1024, "UTF-8")),
				"csv-file.instance", "no-match-instance");
	}

	@Test
	public void uploadStorageNotFound() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> qiuResource.upload(subscription,
						new StringInputStream("ANY;1;1;LINUX;99999999999;BEST;THROUGHPUT", "UTF-8"),
						new String[] { "name", "cpu", "ram", "os", "disk", "latency", "optimized" }, false,
						"Full Time 12 month", 1, "UTF-8")),
				"csv-file.storage", "NotNull");
	}

	@Test
	public void uploadMissingRequiredHeader() {
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, new StringInputStream("ANY", "UTF-8"),
								new String[] { "any" }, false, "Full Time 12 month", 1, "UTF-8")),
				"csv-file", "missing-header");
	}

	@Test
	public void uploadAmbiguousHeader() {
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, new StringInputStream("ANY;ANY", "UTF-8"),
								new String[] { "vcpu", "core" }, false, "Full Time 12 month", 1, "UTF-8")),
				"csv-file", "ambiguous-header");
	}

	@Test
	public void uploadIgnoredInvalidHeader() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;ignored value1;0.5;500;any-value2;LINUX", "UTF-8"),
				new String[] { "name", "ignore", "cpu", "ram", "ignore", "os" }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadAlternativeHeader() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX", "UTF-8"),
				new String[] { "name", "vCPU", "memory", "system" }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadWildcardHeader() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;LINUX", "UTF-8"),
				new String[] { "instance_name", "cpu #", "instance ram (GB)", " os " }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadPriorizedHeader() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("real name;alt. name;2,4;0.5;500;info;LINUX", "UTF-8"),
				new String[] { "\" name  \"", "instance_name", "frequency cpu", "cpus", "instance ram (GB)", "   os(1)",
						"\"os\"" },
				false, null, 1, "UTF-8");
		final var configuration = getConfiguration();
		Assertions.assertEquals("real name", configuration.getInstances().get(7).getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadSoftware() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;WINDOWS;SQL Web", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "software" }, false, "Full Time 12 month", 1, "UTF-8");
		var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C121", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("SQL Web", configuration.getInstances().get(7).getPrice().getSoftware());
	}

	@Test
	public void uploadLicense() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("ANY;0.5;500;WINDOWS;BYOL", "UTF-8"),
				new String[] { "name", "cpu", "ram", "os", "license" }, false, "Full Time 12 month", 1, "UTF-8");
		var configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C120", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("BYOL", configuration.getInstances().get(7).getPrice().getLicense());
	}
}
