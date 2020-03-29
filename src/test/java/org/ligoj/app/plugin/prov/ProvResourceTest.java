/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.app.plugin.prov.quote.storage.QuoteStorageLookup;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class of {@link ProvResource}
 */
public class ProvResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ImportCatalogStatusRepository icsRepository;

	/**
	 * Prepare test data.
	 * 
	 * @throws IOException When CSV cannot be read.
	 */
	@BeforeEach
	protected void prepareData() throws IOException {
		super.prepareData();
		persistEntities("csv", new Class[] { Parameter.class, ParameterValue.class }, StandardCharsets.UTF_8.name());
	}

	@Test
	void testBusiness() {
		// Coverage only
		Assertions.assertEquals(InternetAccess.PUBLIC.ordinal(),
				InternetAccess.valueOf(InternetAccess.values()[0].name()).ordinal());

		// Association only
		Assertions.assertEquals("service:prov:test",
				stRepository.findBy("node.id", "service:prov:test").getNode().getId());
	}

	@Test
	void getSubscriptionStatus() {
		final var status = resource.getSubscriptionStatus(subscription);
		Assertions.assertEquals("quote1", status.getName());
		Assertions.assertEquals("quoteD1", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost(status.getCost(), 4704.758, 7154.358, false);
		Assertions.assertEquals(7, status.getNbInstances());
		Assertions.assertEquals(0, status.getNbDatabases());
		Assertions.assertEquals(10.75, status.getTotalCpu(), 0.0001);
		Assertions.assertEquals(45576, status.getTotalRam());
		Assertions.assertEquals(6, status.getNbPublicAccess());
		Assertions.assertEquals(7, status.getNbStorages()); // 3*2 (server1) + 1
		Assertions.assertEquals(175, status.getTotalStorage());
		Assertions.assertEquals("region-1", status.getLocation().getName());
		Assertions.assertEquals("USD", status.getCurrency().getName());
	}

	@Test
	void getSubscriptionStatusEmpty() {
		final var status = resource.getSubscriptionStatus(getSubscription("mda", ProvResource.SERVICE_KEY));
		Assertions.assertEquals("quote2", status.getName());
		Assertions.assertEquals("quoteD2", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost0(status.getCost());
		Assertions.assertEquals(0, status.getNbInstances());
		Assertions.assertEquals(0, status.getNbDatabases());
		Assertions.assertEquals(0, status.getTotalCpu(), 0.0001);
		Assertions.assertEquals(0, status.getTotalRam());
		Assertions.assertEquals(0, status.getNbStorages());
		Assertions.assertEquals(0, status.getTotalStorage());
	}

	@Test
	void getConfigurationTest() {
		var vo = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote1", vo.getName());
		Assertions.assertEquals("quoteD1", vo.getDescription());
		Assertions.assertEquals("USD", vo.getCurrency().getName());
		checkCost(vo.getCost(), 4704.758, 7154.358, false);
		checkCost(resource.updateCost(subscription), 4704.758, 7154.358, false);
		vo = resource.getConfiguration(subscription);
		checkCost(vo.getCost(), 4704.758, 7154.358, false);

		Assertions.assertNull(vo.getTerraformStatus());
		Assertions.assertNotNull(vo.getId());
		Assertions.assertNotNull(vo.getCreatedBy());
		Assertions.assertNotNull(vo.getCreatedDate());
		Assertions.assertNotNull(vo.getLastModifiedBy());
		Assertions.assertNotNull(vo.getLastModifiedDate());
		Assertions.assertEquals("region-1", vo.getLocation().getName());
		Assertions.assertEquals(3, vo.getLocations().size());
		Assertions.assertEquals("region-1", vo.getLocations().get(0).getName());
		Assertions.assertEquals("region-2", vo.getLocations().get(1).getName());
		Assertions.assertEquals("region-5", vo.getLocations().get(2).getName());

		// Processor
		Assertions.assertEquals(2, vo.getProcessors().size());
		Assertions.assertEquals(3, vo.getProcessors().get("instance").size());
		Assertions.assertEquals(0, vo.getProcessors().get("database").size());

		// Check compute
		final var instances = vo.getInstances();
		Assertions.assertEquals(7, instances.size());
		final var quoteInstance = instances.get(0);
		Assertions.assertNotNull(quoteInstance.getId());
		Assertions.assertEquals("server1", quoteInstance.getName());
		Assertions.assertEquals("serverD1", quoteInstance.getDescription());
		Assertions.assertTrue(quoteInstance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, quoteInstance.getInternet());
		Assertions.assertEquals(10.1, quoteInstance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(2, quoteInstance.getMinQuantity());
		Assertions.assertEquals(10, quoteInstance.getMaxQuantity().intValue());
		final var price = quoteInstance.getPrice();
		Assertions.assertEquals(146.4, price.getCost(), DELTA);
		Assertions.assertEquals(146.4, price.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, price.getOs());
		Assertions.assertNotNull(price.getTerm().getId());
		Assertions.assertFalse(price.getTerm().isEphemeral());
		Assertions.assertFalse(price.getTerm().isVariable());
		Assertions.assertEquals(0, price.getTerm().getPeriod());
		Assertions.assertEquals("on-demand1", price.getTerm().getName());
		Assertions.assertEquals("15 minutes fragment", price.getTerm().getDescription());
		final var instance = price.getType();
		Assertions.assertNotNull(instance.getId());
		Assertions.assertEquals("instance1", instance.getName());
		Assertions.assertEquals("instanceD1", instance.getDescription());
		Assertions.assertEquals(0.5, instance.getCpu(), 0.0001);
		Assertions.assertEquals(2000, instance.getRam().intValue());
		Assertions.assertTrue(instance.getConstant());

		// No minimal for this instance price
		Assertions.assertNull(instances.get(1).getMaxVariableCost());

		Assertions.assertEquals(1, instances.get(3).getMinQuantity());
		Assertions.assertEquals(1, instances.get(3).getMaxQuantity().intValue());

		// Check the constant CPU requirement
		Assertions.assertTrue(instances.get(0).getConstant());
		Assertions.assertNull(instances.get(1).getConstant());
		Assertions.assertFalse(instances.get(3).getConstant());

		// Check the network requirement
		Assertions.assertEquals(InternetAccess.PUBLIC, instances.get(0).getInternet());
		Assertions.assertEquals(InternetAccess.PRIVATE, instances.get(1).getInternet());
		Assertions.assertEquals(InternetAccess.PRIVATE_NAT, instances.get(2).getInternet());

		// Check storage
		final var storages = vo.getStorages();
		Assertions.assertEquals(4, storages.size());
		final var quoteStorage = storages.get(0);
		Assertions.assertNotNull(quoteStorage.getId());
		Assertions.assertEquals("server1-root", quoteStorage.getName());
		Assertions.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assertions.assertEquals(20, quoteStorage.getSize());
		Assertions.assertEquals(8.4, quoteStorage.getCost(), DELTA);
		Assertions.assertEquals(42, quoteStorage.getMaxCost(), DELTA); // = 8.4 * 5
		Assertions.assertNotNull(quoteStorage.getQuoteInstance());
		final var storage = quoteStorage.getPrice();
		final var storageType = storage.getType();
		Assertions.assertNotNull(storage.getId());
		Assertions.assertEquals(0.21, storage.getCostGb(), DELTA);
		Assertions.assertEquals(0, storage.getCost(), DELTA);
		Assertions.assertEquals("storage1", storageType.getName());
		Assertions.assertEquals("storageD1", storageType.getDescription());
		Assertions.assertEquals(200, storageType.getIops());
		Assertions.assertEquals(60, storageType.getThroughput());
		Assertions.assertEquals(0, storage.getCostTransaction(), DELTA);
		Assertions.assertEquals(Rate.GOOD, storageType.getLatency());
		Assertions.assertEquals(ProvStorageOptimized.IOPS, storageType.getOptimized());

		// Not attached storage
		Assertions.assertNull(storages.get(3).getQuoteInstance());

		// Check the small transactional cost
		Assertions.assertEquals(0.000000072, storages.get(1).getPrice().getCostTransaction(), 0.000000001);

		// Check the related instance and price for the next comparison
		Assertions.assertEquals("instance1", instances.get(0).getPrice().getType().getName());
		Assertions.assertEquals("instance1", instances.get(1).getPrice().getType().getName());
		Assertions.assertEquals("instance1", instances.get(2).getPrice().getType().getName());
		Assertions.assertEquals("instance3", instances.get(3).getPrice().getType().getName());
		Assertions.assertEquals("instance5", instances.get(4).getPrice().getType().getName());
		Assertions.assertEquals("instance10", instances.get(5).getPrice().getType().getName());
		Assertions.assertEquals("dynamic", instances.get(6).getPrice().getType().getName());

		Assertions.assertEquals("on-demand1", instances.get(0).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand2", instances.get(1).getPrice().getTerm().getName());
		Assertions.assertEquals("1y", instances.get(2).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances.get(3).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand2", instances.get(4).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances.get(5).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances.get(6).getPrice().getTerm().getName());

		// Check databases
		final var databases = vo.getInstances();
		Assertions.assertEquals(7, databases.size());

		// Optimize the configuration
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		final var vo2 = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote1", vo2.getName());
		Assertions.assertEquals(vo2.getId(), vo.getId());

		// Check the new instances
		final var instances2 = vo2.getInstances();
		Assertions.assertEquals(7, instances2.size());

		// Same instance
		Assertions.assertEquals("instance1", instances2.get(0).getPrice().getType().getName());
		Assertions.assertEquals("dynamic", instances2.get(5).getPrice().getType().getName());
		Assertions.assertEquals("dynamic", instances2.get(4).getPrice().getType().getName());

		// Fixed instance types for the same constraints
		Assertions.assertEquals("instance2", instances2.get(1).getPrice().getType().getName());
		Assertions.assertEquals("instance2", instances2.get(2).getPrice().getType().getName());
		Assertions.assertEquals("instance2", instances2.get(3).getPrice().getType().getName());
		Assertions.assertEquals("dynamic", instances2.get(6).getPrice().getType().getName());

		// Check the contracts are the same but for 2
		Assertions.assertEquals("on-demand1", instances2.get(0).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand2", instances2.get(1).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances2.get(2).getPrice().getTerm().getName()); // Updated
		Assertions.assertEquals("on-demand1", instances2.get(3).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances2.get(4).getPrice().getTerm().getName()); // Updated
		Assertions.assertEquals("on-demand1", instances2.get(5).getPrice().getTerm().getName());
		Assertions.assertEquals("on-demand1", instances2.get(6).getPrice().getTerm().getName());

		// No associated usage for this use case
		Assertions.assertNull(vo.getUsage());
		Assertions.assertNotNull(vo.getUsages());

		// No networks
		Assertions.assertTrue(vo.getNetworks().isEmpty());
	}

	@Test
	void getConfigurationEmpty() {
		checkCost0(resource.updateCost(checkEmpty()));
		checkCost0(resource.refresh(checkEmpty()));
	}

	private int checkEmpty() {
		final var subscription = getSubscription("mda", ProvResource.SERVICE_KEY);
		final var vo = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote2", vo.getName());
		Assertions.assertEquals("quoteD2", vo.getDescription());
		Assertions.assertNull(vo.getCurrency());
		Assertions.assertNotNull(vo.getId());
		Assertions.assertNull(vo.getLicense());
		checkCost0(vo.getCost());

		// Check compute
		Assertions.assertEquals(0, vo.getInstances().size());

		// Check storage
		Assertions.assertEquals(0, vo.getStorages().size());
		return subscription;
	}

	private void checkCost0(final FloatingCost cost) {
		checkCost(cost, 0, 0, false);
	}

	protected QuoteLightVo checkCost(final int subscription, final double min, final double max,
			final boolean unbound) {
		final var status = super.checkCost(subscription, min, max, unbound);
		final var quote = repository.findByNameExpected(status.getName());
		Assertions.assertSame(unbound, quote.isUnboundCost());
		Assertions.assertSame(quote, quote.getConfiguration());
		return status;
	}

	/**
	 * Update the location of the quote, impact all instances, but no one use the default location. Cost still updated.
	 */
	@Test
	void updateLocation() {
		final var location4 = locationRepository.findByName("region-4");

		// Make sure there is no more world wild prices
		em.createQuery("FROM ProvInstancePrice WHERE location IS NULL", ProvInstancePrice.class).getResultList()
				.forEach(ip -> ip.setLocation(location4));
		em.flush();
		em.clear();

		final var quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-1");
		quote.setRefresh(true);
		final var cost = resource.update(subscription, quote);
		checkCost(cost, 6328.565, 10199.018, false);
		var quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());

		// Check location
		final var location = quote2.getLocation();
		Assertions.assertEquals("region-1", location.getName());
		Assertions.assertEquals("west", location.getPlacement());
		Assertions.assertEquals(840, location.getCountryM49().intValue());
		Assertions.assertEquals("US", location.getCountryA2());
		Assertions.assertEquals(21, location.getRegionM49().intValue());
		Assertions.assertEquals(19, location.getContinentM49().intValue());
		Assertions.assertEquals("Virginia", location.getSubRegion());
		Assertions.assertEquals(37.352d, location.getLongitude(), DELTA);
		Assertions.assertEquals(-79.049d, location.getLatitude(), DELTA);

		// CHeck the association on the quote
		Assertions.assertEquals("region-1", resource.getConfiguration(subscription).getLocation().getName());

		// Check the "region-1" is the one related to our provider
		Assertions.assertEquals("service:prov:test", repository.findByName("name1").getLocation().getNode().getId());
	}

	private ProvQuote newProvQuote() {
		final var subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);

		final var configuration = new ProvQuote();
		configuration.setSubscription(subscription);
		configuration.setName("new");
		final var provider = subscription.getNode().getRefined();
		configuration.setLocation(locationRepository.findAllBy("node.id", provider.getId()).get(0));
		em.persist(configuration);

		final var usage = new ProvUsage();
		usage.setConfiguration(configuration);
		usage.setDuration(12);
		usage.setRate(100);
		usage.setName("usage");
		em.persist(usage);

		configuration.setUsage(usage);
		em.merge(configuration);

		final var instance = new ProvQuoteInstance();
		instance.setConfiguration(configuration);
		instance.setCpu(1D);
		instance.setRam(2000);
		instance.setCpuMax(0.5D);
		instance.setRamMax(1000);
		instance.setName("instance");
		instance.setOs(VmOs.WINDOWS);
		instance.setCost(0D);
		instance.setMaxCost(0D);
		instance.setPrice(ipRepository.findBy("code", "C12"));
		em.persist(instance);
		em.flush();
		em.clear();

		// Check the configuration before the update
		checkCost(resource.refresh(subscription.getId()), 175.68, 175.68, false);
		final var instanceGet = resource.getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet.getPrice().getCode());

		return configuration;
	}

	/**
	 * Update the usage rate.
	 */
	@Test
	void updateUsage() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();

		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setUsage("usage");
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);

		// Refresh with usage changed
		final var usage2 = new ProvUsage();
		usage2.setConfiguration(configuration);
		usage2.setDuration(1);
		usage2.setRate(20);
		usage2.setName("usage2");
		em.persist(usage2);
		em.flush();
		em.clear();
		quote.setUsage("usage2");
		checkCost(resource.update(subscription.getId(), quote), 47.58, 47.58, false);
		em.flush();
		em.clear();
	}

	/**
	 * Update the RAM adjust rate.
	 */
	@Test
	void updateRamAdjustRate() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();

		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setUsage("usage");
		quote.setRamAdjustedRate(100);
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);
		final var instanceGet2 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet2.getPrice().getCode());

		quote.setRamAdjustedRate(50);
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);
		final var instanceGet3 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet3.getPrice().getCode());

		quote.setRamAdjustedRate(150);
		checkCost(resource.update(subscription.getId(), quote), 702.72, 702.72, false);
		final var instanceGet4 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C36", instanceGet4.getPrice().getCode());
		Assertions.assertEquals(150, resource.getConfiguration(subscription.getId()).getRamAdjustedRate());
	}

	/**
	 * Update the resource mode.
	 */
	@Test
	void updateResourceMode() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();
		var instanceGet = resource.getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet.setCpu(2);
		instanceGet.setRam(4000);
		em.flush();
		em.clear();
		checkCost(resource.refresh(configuration), 1405.44, 1405.44, false);

		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setUsage("usage");
		quote.setReservationMode(ReservationMode.RESERVED);
		quote.setRefresh(true);
		checkCost(resource.update(subscription.getId(), quote), 1405.44, 1405.44, false);
		var quoteVo = getConfiguration(subscription.getId());
		Assertions.assertEquals(ReservationMode.RESERVED, quoteVo.getReservationMode());
		final var instanceGet2 = quoteVo.getInstances().get(0);
		Assertions.assertEquals("C48", instanceGet2.getPrice().getCode());

		quote.setRefresh(false);
		quote.setReservationMode(ReservationMode.MAX);
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);
		quoteVo = getConfiguration(subscription.getId());
		Assertions.assertEquals(ReservationMode.MAX, quoteVo.getReservationMode());
		final var instanceGet3 = quoteVo.getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet3.getPrice().getCode());

		instanceGet = resource.getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet.setCpuMax(null);
		instanceGet.setRamMax(null);
		em.flush();
		em.clear();
		quote.setRefresh(true);
		checkCost(resource.update(subscription.getId(), quote), 1405.44, 1405.44, false);
	}

	/**
	 * Update the processor.
	 */
	@Test
	void updateProcessor() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();
		var instanceGet = resource.getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet.setProcessor("Intel Xeon");
		instanceGet.setOs(VmOs.LINUX);
		em.flush();
		em.clear();
		checkCost(resource.refresh(configuration), 3513.6, 3513.6, false);

		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setUsage("usage");
		quote.setProcessor("AMD");
		checkCost(resource.update(subscription.getId(), quote), 3513.6, 3513.6, false);
		var quoteVo = getConfiguration(subscription.getId());
		Assertions.assertEquals("AMD", quoteVo.getProcessor());
		final var instanceGet0 = quoteVo.getInstances().get(0);
		Assertions.assertEquals("C65", instanceGet0.getPrice().getCode());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", instanceGet0.getPrice().getType().getProcessor());

		// Refresh without change
		quote.setRefresh(true);
		checkCost(resource.update(subscription.getId(), quote), 3513.6, 3513.6, false);

		// Remove local requirement
		var instanceGet1 = getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet1.setProcessor(null);
		em.flush();
		em.clear();
		checkCost(resource.refresh(configuration), 249.343, 249.343, false);
		var quoteVo2 = getConfiguration(subscription.getId());
		final var instanceGet2 = quoteVo2.getInstances().get(0);
		Assertions.assertEquals("C74", instanceGet2.getPrice().getCode());
		Assertions.assertEquals("AMD EPYC 7571", instanceGet2.getPrice().getType().getProcessor());
		em.flush();
		em.clear();

		// Remove global requirement
		quote.setProcessor(null);
		quote.setRefresh(false);
		checkCost(resource.update(subscription.getId(), quote), 102.48, 102.48, false);
	}

	/**
	 * Update the physical host constraint.
	 */
	@Test
	void updatePhysical() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();
		var instanceGet = resource.getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet.setPhysical(true);
		instanceGet.setOs(VmOs.LINUX);
		em.flush();
		em.clear();
		checkCost(resource.refresh(configuration), 878.4, 878.4, false);

		// Requires all quotes are "physical"
		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setUsage("usage");
		quote.setPhysical(true);
		checkCost(resource.update(subscription.getId(), quote), 878.4, 878.4, false);
		var quoteVo = getConfiguration(subscription.getId());
		Assertions.assertTrue(quoteVo.getPhysical());
		final var instanceGet0 = quoteVo.getInstances().get(0);
		Assertions.assertEquals("C41", instanceGet0.getPrice().getCode());
		Assertions.assertTrue(instanceGet0.getPrice().getType().getPhysical());

		// Remove local requirement -> no change because of global requirement
		var instanceGet1 = getConfiguration(subscription.getId()).getInstances().get(0);
		instanceGet1.setPhysical(null);
		em.flush();
		em.clear();
		checkCost(resource.refresh(configuration), 878.4, 878.4, false);
		checkCost(resource.update(subscription.getId(), quote), 878.4, 878.4, false);

		// Remove global requirement
		quote.setPhysical(null);
		checkCost(resource.update(subscription.getId(), quote), 102.48, 102.48, false);
		var quoteVo2 = getConfiguration(subscription.getId());
		final var instanceGet2 = quoteVo2.getInstances().get(0);
		Assertions.assertEquals("C11", instanceGet2.getPrice().getCode());
		Assertions.assertFalse(instanceGet2.getPrice().getType().getPhysical());
	}

	/**
	 * Update the default license model of the quote, impact all instances using the default license model.
	 */
	@Test
	void updateLicense() {
		final var configuration = newProvQuote();
		final var subscription = configuration.getSubscription();

		final var quote = new QuoteEditionVo();
		quote.setName("new1");
		quote.setLocation(configuration.getLocation().getName());
		quote.setLicense("BYOL");
		quote.setUsage("usage");
		checkCost(resource.update(subscription.getId(), quote), 102.49, 102.49, false);
		final var instanceGet2 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C120", instanceGet2.getPrice().getCode());

		quote.setLicense("INCLUDED");
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);
		final var instanceGet3 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet3.getPrice().getCode());

		quote.setLicense(null);
		checkCost(resource.update(subscription.getId(), quote), 175.68, 175.68, false);
		final var instanceGet4 = getConfiguration(subscription.getId()).getInstances().get(0);
		Assertions.assertEquals("C12", instanceGet4.getPrice().getCode());
	}

	/**
	 * Update the location of the quote, impact all instances using the default location. Cost still updated.
	 */
	@Test
	void updateLocationDifferentQILocation() {
		final var location = locationRepository.findByName("region-1");
		final var location4 = locationRepository.findByName("region-4");

		// Change the required location of all quote instance
		qiRepository.findAll().forEach(ip -> ip.setLocation(location));

		// Make sure there is no more world wild prices
		em.createQuery("FROM ProvInstancePrice WHERE location.name=:location", ProvInstancePrice.class)
				.setParameter("location", "region-1").getResultList().forEach(ip -> ip.setLocation(location4));
		em.createQuery("FROM ProvStoragePrice WHERE location.name=:location", ProvStoragePrice.class)
				.setParameter("location", "region-1").getResultList().forEach(ip -> ip.setLocation(location4));
		em.createQuery("FROM ProvQuoteInstance WHERE location.name=:location", ProvQuoteInstance.class)
				.setParameter("location", "region-1").getResultList().forEach(ip -> ip.setLocation(location4));
		em.flush();
		em.clear();
		clearAllCache();

		// New cost based on region-4
		final var quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-4");
		final var cost = resource.update(subscription, quote);
		checkCost(cost, 3165.4, 5615.0, false);
		final var quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());
		Assertions.assertEquals("region-4", quote2.getLocation().getName());
	}

	/**
	 * Update to an unknown location.
	 */
	@Test
	void updateLocationNotExists() {
		final var quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-x");
		Assertions.assertThrows(EntityNotFoundException.class, () -> resource.update(subscription, quote));
	}

	/**
	 * Update the location related to another provider.
	 */
	@Test
	void updateLocationNotExistsForThisSubscription() {
		final var quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-3");
		Assertions.assertThrows(EntityNotFoundException.class, () -> resource.update(subscription, quote));
	}

	@Test
	void getKey() {
		Assertions.assertEquals("service:prov", resource.getKey());

		// Only there for coverage of associations required by JPA
		new ProvQuote().setStorages(null);
		new ProvQuote().getStorages();
		new ProvQuote().getDatabases();
		new ProvQuote().getTags();
		new ProvQuote().setTags(null);
		new ProvQuote().setSupports(null);
		new ProvQuote().setInstances(null);
		new ProvQuote().setDatabases(null);
		new ProvQuoteInstance().setStorages(null);
		new UpdatedCost(0).setDeleted(null);
		Rate.valueOf(Rate.GOOD.name());
		ProvStorageOptimized.valueOf(ProvStorageOptimized.IOPS.name());
		VmOs.valueOf(VmOs.LINUX.name());
		ProvTenancy.valueOf(ProvTenancy.DEDICATED.name());
	}

	@Test
	void delete() {
		// Check the pre-deletion
		Assertions.assertEquals(3, repository.findAll().size());

		em.flush();
		em.clear();

		resource.delete(subscription, true);
		em.flush();
		em.clear();

		// Check the post-deletion
		Assertions.assertEquals(2, repository.findAll().size());
	}

	@Test
	void deleteNoConfiguration() {
		// Check the pre-deletion
		repository.deleteAll();
		Assertions.assertEquals(0, repository.findAll().size());
		em.flush();
		em.clear();

		resource.delete(subscription, true);
		em.flush();
		em.clear();

		// Check the post-deletion
		Assertions.assertEquals(0, repository.findAll().size());
	}

	@Test
	void testToString() {
		final var computedInstancePrice = new QuoteInstanceLookup();
		computedInstancePrice.setCost(1.23);
		final var ip = new ProvInstancePrice();
		final var type = new ProvInstancePriceTerm();
		type.setName("type1");
		ip.setTerm(type);
		final var instance = new ProvInstanceType();
		instance.setName("instance1");
		ip.setType(instance);
		computedInstancePrice.setPrice(ip);
		Assertions.assertTrue(computedInstancePrice.toString().contains("cost=1.23"));
		Assertions.assertTrue(computedInstancePrice.toString().contains("name=instance1"));

		final var computedStoragePrice = new QuoteStorageLookup();
		computedStoragePrice.setCost(1.23);
		final var sp = new ProvStoragePrice();
		final var sType = new ProvStorageType();
		sType.setName("type1");
		sp.setType(sType);
		computedStoragePrice.setPrice(sp);
		Assertions.assertTrue(computedStoragePrice.toString().contains("cost=1.23"));
		Assertions.assertTrue(computedStoragePrice.toString().contains("name=type1"));
	}

	@Test
	void findLocations() {
		final var locations = resource.findLocations(subscription);

		// 3 regions, but only 2 have associated prices
		Assertions.assertEquals(3, locations.size());
		Assertions.assertEquals("region-1", locations.get(0).getName());
		Assertions.assertEquals("region-2", locations.get(1).getName());
		Assertions.assertEquals("service:prov:test", locations.get(1).getNode().getId());
	}

	@Test
	void checkSubscriptionStatus() {
		final AbstractProvResource res = new AbstractProvResource() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		};
		res.provResource = resource;
		final var quote = (QuoteLightVo) res.checkSubscriptionStatus(subscription, null, Collections.emptyMap())
				.getData().get("quote");
		Assertions.assertNotNull(quote);
		checkCost(quote.getCost(), 4704.758, 7154.358, false);
	}

	@Test
	void create() {
		final var subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		em.flush();
		em.clear();
		resource.create(subscription.getId());
		final var configuration = resource.getConfiguration(subscription.getId());
		Assertions.assertNotNull(configuration);
		Assertions.assertNotNull(configuration.getName());
		Assertions.assertNotNull(configuration.getDescription());
		Assertions.assertNull(configuration.getCurrency());
	}

	@Test
	void createCurrency() {
		final var subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);

		final var parameterValue = new ParameterValue();
		parameterValue.setParameter(em.createQuery("FROM Parameter WHERE id=:id", Parameter.class)
				.setParameter("id", "service:prov:currency").getSingleResult());
		parameterValue.setData("USD");
		parameterValue.setNode(em.createQuery("FROM Node WHERE id=:id", Node.class)
				.setParameter("id", "service:prov:test:account").getSingleResult());
		parameterValue.setSubscription(subscription);
		em.persist(parameterValue);

		em.flush();
		em.clear();
		resource.create(subscription.getId());
		final var configuration = resource.getConfiguration(subscription.getId());
		Assertions.assertNotNull(configuration);
		Assertions.assertNotNull(configuration.getName());
		Assertions.assertNotNull(configuration.getDescription());
		Assertions.assertEquals("USD", configuration.getCurrency().getName());
	}

	@Test
	void useParallel() {
		final var subscription = new Subscription();
		configuration.put(ProvResource.USE_PARALLEL, "0");
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		em.flush();
		em.clear();
		resource.create(subscription.getId());
		final var configuration = resource.getConfiguration(subscription.getId());
		Assertions.assertNotNull(configuration);
		Assertions.assertNotNull(configuration.getName());
		Assertions.assertNotNull(configuration.getDescription());
		checkCost(resource.refresh(subscription.getId()), 0, 0, false);
	}

	@Test
	void createNoCatalog() {
		final var subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		qsRepository.deleteAll();
		qiRepository.deleteAll();
		ipRepository.deleteAll();
		iptRepository.deleteAll();
		itRepository.deleteAll();
		spRepository.deleteAll();
		stRepository.deleteAll();
		icsRepository.deleteAll();
		repository.deleteAll();
		locationRepository.deleteAll();
		em.flush();
		em.clear();
		Assertions.assertEquals("service:prov-no-catalog", Assertions
				.assertThrows(BusinessException.class, () -> resource.create(subscription.getId())).getMessage());
	}

	@Test
	void update() {
		final var quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-1");
		quote.setRefresh(true);
		final var cost = resource.update(subscription, quote);
		checkCost(cost, 3165.4, 5615.0, false);
		var quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());
		Assertions.assertEquals("region-1", quote2.getLocation().getName());

		// Performe another identical update
		final var cost2 = resource.update(subscription, quote);
		checkCost(cost2, 3165.4, 5615.0, false);
	}

	@Test
	void findConfigured() {
		final var qi = qiRepository.findByName("server1");
		Assertions.assertEquals("server1", resource.findConfigured(qiRepository, qi.getId(), subscription).getName());
	}

	@Test
	void findConfiguredByName() {
		Assertions.assertEquals("server1",
				resource.findConfiguredByName(qiRepository, "server1", subscription).getName());
	}

	@Test
	void findConfiguredNotFound() {
		final var qi = qiRepository.findByName("server1");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> resource.findConfigured(qiRepository, qi.getId(), 0).getName());
	}

	@Test
	void findConfiguredByNameNotFoundInvalidName() {
		Assertions.assertEquals("serverAAAAA",
				Assertions
						.assertThrows(EntityNotFoundException.class,
								() -> resource.findConfiguredByName(qiRepository, "serverAAAAA", subscription))
						.getMessage());
	}

	@Test
	void findConfiguredByNameNotFoundInvalidSub() {
		Assertions.assertEquals("server1", Assertions.assertThrows(EntityNotFoundException.class,
				() -> resource.findConfiguredByName(qiRepository, "server1", 0)).getMessage());
	}

	@Test
	void getInstalledEntities() {
		Assertions.assertTrue(resource.getInstalledEntities().contains(SystemConfiguration.class));
		Assertions.assertTrue(resource.getInstalledEntities().contains(Node.class));
	}
}
