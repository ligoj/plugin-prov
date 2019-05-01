/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceUploadResource;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

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
		final QuoteVo configuration = checkUpload();
		Assertions.assertEquals(10.1d, configuration.getInstances().get(0).getMaxVariableCost(), DELTA);
	}

	private QuoteVo checkUpload() {
		final QuoteVo configuration = getConfiguration();
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
		final QuoteVo configuration = getConfiguration();
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
		final StringBuilder content = new StringBuilder();
		final String headers = "name;cpu;ram;disk;os";
		content.append(headers + "\n");
		for (int i = 0; i < 20; i++) {
			content.append("name").append(i).append(";1;1;1;LINUX\n");
		}
		qiuResource.upload(subscription, new StringInputStream(content.toString(), "UTF-8"), null, true,
				"Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(27, configuration.getInstances().size());
	}

	@Test
	public void uploadFixedInstanceType() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "ephemeral" }, false, "Full Time 12 month", 1,
				"UTF-8");
		QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvInstancePriceTerm term = configuration.getInstances().get(7).getPrice().getTerm();
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
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1000;true".getBytes("UTF-8")), new String[] {
						"name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7); // The last one
		Assertions.assertEquals("ANY", qi.getName());
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals("1y", qi.getPrice().getTerm().getName());
		Assertions.assertFalse(qi.getPrice().getTerm().isVariable());
		Assertions.assertEquals(1000, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 117164.358, false);
		final Map<String, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 210, false);
	}

	private QuoteVo getConfiguration() {
		em.flush();
		em.clear();
		final QuoteVo configuration = resource.getConfiguration(subscription);
		return configuration;
	}

	@Test
	public void uploadMaxQuantities() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1;true".getBytes("UTF-8")), new String[] { "name",
						"cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, false);
		final Map<String, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, false);
	}

	@Test
	public void uploadMultipleDisks() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("MYINSTANCE;0.5;500;LINUX;1,0,10;true;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "ephemeral" }, false,
				"Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(6, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4816.868, 7266.468, false);
		final Map<String, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("MYINSTANCE");
		Assertions.assertEquals(2, storagesFloatingCost.size()); // 1GB and 10GB disks
		checkCost(storagesFloatingCost.get("MYINSTANCE"), 0.21, 0.21, false);
		Assertions.assertEquals("MYINSTANCE", qsRepository.findAllBy("cost", .21d).get(0).getName());
		checkCost(storagesFloatingCost.get("MYINSTANCE2"), 2.1, 2.1, false);
		Assertions.assertEquals("MYINSTANCE2", qsRepository.findAllBy("cost", 2.1d).get(0).getName());
	}

	@Test
	public void uploadUnBoundQuantities() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;0;true".getBytes("UTF-8")), new String[] { "name",
						"cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity());
		Assertions.assertNull(qi.getMaxQuantity());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4814.768, 7264.368, true);
		final Map<String, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, true);
	}

	@Test
	public void uploadInternetAccess() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;PUBLIC;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "internet", "ephemeral" }, false,
				"Full Time 12 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
	}

	@Test
	public void uploadDefaultUsage() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os" }, false, null, 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadUsagePerEntry() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;Full Time 12 month".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 13 month", 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance2", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals("1y", configuration.getInstances().get(7).getPrice().getTerm().getName());
		checkCost(configuration.getCost(), 4807.238, 7256.838, false);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;999;6;LINUX".getBytes("UTF-8")), null, false,
				"Full Time 12 month", 1024, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 247315.131, 249764.731, false);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;1;64;LINUX".getBytes("UTF-8")), null, false,
				"Full Time 12 month", 1024, "UTF-8");
		final QuoteVo configuration = getConfiguration();
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
		Assertions.assertEquals("Full Time2",
				Assertions.assertThrows(EntityNotFoundException.class, () -> qiuResource.upload(subscription,
						new ByteArrayInputStream("ANY;0.5;500;LINUX;Full Time2".getBytes("UTF-8")),
						new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1, "UTF-8"))
						.getMessage());
	}

	/**
	 * Expected location does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	public void uploadInvalidLocationForSubscription() {
		Assertions.assertEquals("region-3",
				Assertions.assertThrows(EntityNotFoundException.class,
						() -> qiuResource.upload(subscription,
								new ByteArrayInputStream("ANY;0.5;500;LINUX;region-3".getBytes("UTF-8")),
								new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1,
								"UTF-8"))
						.getMessage());
	}

	/**
	 * Expected location does not exist at all?
	 */
	@Test
	public void uploadInvalidLocation() {
		Assertions.assertEquals("region-ZZ",
				Assertions.assertThrows(EntityNotFoundException.class,
						() -> qiuResource.upload(subscription,
								new ByteArrayInputStream("ANY;0.5;500;LINUX;region-ZZ".getBytes("UTF-8")),
								new String[] { "name", "cpu", "ram", "os", "location" }, false, "Full Time 12 month", 1,
								"UTF-8"))
						.getMessage());
	}

	/**
	 * Expected usage does not exist at all.
	 */
	@Test
	public void uploadInvalidUsage() {
		Assertions.assertEquals("any",
				Assertions.assertThrows(EntityNotFoundException.class, () -> qiuResource.upload(subscription,
						new ByteArrayInputStream("ANY;0.5;500;LINUX;any".getBytes("UTF-8")),
						new String[] { "name", "cpu", "ram", "os", "usage" }, false, "Full Time 12 month", 1, "UTF-8"))
						.getMessage());
	}

	@Test
	public void uploadInstanceNotFound() {
		MatcherUtil
				.assertThrows(
						Assertions.assertThrows(ValidationJsonException.class,
								() -> qiuResource.upload(subscription,
										new ByteArrayInputStream("ANY;999;6;WINDOWS".getBytes("UTF-8")), null, false,
										"Full Time 12 month", 1024, "UTF-8")),
						"csv-file.instance", "no-match-instance");
	}

	@Test
	public void uploadStorageNotFound() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class,
				() -> qiuResource.upload(subscription,
						new ByteArrayInputStream("ANY;1;1;LINUX;99999999999;BEST;THROUGHPUT".getBytes("UTF-8")),
						new String[] { "name", "cpu", "ram", "os", "disk", "latency", "optimized" }, false,
						"Full Time 12 month", 1, "UTF-8")),
				"csv-file.storage", "NotNull");
	}

	@Test
	public void uploadMissingRequiredHeader() {
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, new ByteArrayInputStream("ANY".getBytes("UTF-8")),
								new String[] { "any" }, false, "Full Time 12 month", 1, "UTF-8")),
				"csv-file", "missing-header");
	}

	@Test
	public void uploadAmbiguousHeader() {
		MatcherUtil.assertThrows(
				Assertions.assertThrows(ValidationJsonException.class,
						() -> qiuResource.upload(subscription, new ByteArrayInputStream("ANY;ANY".getBytes("UTF-8")),
								new String[] { "vcpu", "core" }, false, "Full Time 12 month", 1, "UTF-8")),
				"csv-file", "ambiguous-header");
	}

	@Test
	public void uploadIgnoredInvalidHeader() throws IOException {
		qiuResource.upload(subscription,
				new ByteArrayInputStream("ANY;ignored value1;0.5;500;any-value2;LINUX".getBytes("UTF-8")),
				new String[] { "name", "ignore", "cpu", "ram", "ignore", "os" }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadAlternativeHeader() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX".getBytes("UTF-8")),
				new String[] { "name", "vCPU", "memory", "system" }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadWildcardHeader() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX".getBytes("UTF-8")),
				new String[] { "instance_name", "cpu #", "instance ram (GB)", " os " }, false, null, 1, "UTF-8");
		checkCost(resource.getConfiguration(subscription).getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadPriorizedHeader() throws IOException {
		qiuResource.upload(subscription, new StringInputStream("real name;alt. name;2,4;0.5;500;info;LINUX", "UTF-8"),
				new String[] { "\" name  \"", "instance_name", "frequency cpu", "cpus", "instance ram (GB)", "   os(1)",
						"\"os\"" },
				false, null, 1, "UTF-8");
		final QuoteVo configuration = getConfiguration();
		Assertions.assertEquals("real name", configuration.getInstances().get(7).getName());
		checkCost(configuration.getCost(), 4840.178, 7289.778, false);
	}

	@Test
	public void uploadSoftware() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;WINDOWS;SQL Web".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "software" }, false, "Full Time 12 month", 1, "UTF-8");
		QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C121", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("SQL Web", configuration.getInstances().get(7).getPrice().getSoftware());
	}

	@Test
	public void uploadLicense() throws IOException {
		qiuResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;WINDOWS;BYOL".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "license" }, false, "Full Time 12 month", 1, "UTF-8");
		QuoteVo configuration = getConfiguration();
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("C120", configuration.getInstances().get(7).getPrice().getCode());
		Assertions.assertEquals("BYOL", configuration.getInstances().get(7).getPrice().getLicense());
	}
}
