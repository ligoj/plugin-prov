package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link ProvResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvInstancePriceTermRepository iptRepository;
	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	private int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		refreshCost();
	}

	@Test
	public void testBusiness() {
		// Coverage only
		Assert.assertEquals(InternetAccess.PUBLIC.ordinal(), InternetAccess.valueOf(InternetAccess.values()[0].name()).ordinal());

		// Association only
		Assert.assertEquals("service:prov:test", stRepository.findBy("node.id", "service:prov:test").getNode().getId());
	}

	@Test
	public void getSusbcriptionStatus() {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		Assert.assertEquals("quote1", status.getName());
		Assert.assertEquals("quoteD1", status.getDescription());
		Assert.assertNotNull(status.getId());
		checkCost(status.getCost(), 4692.785, 7139.185, false);
		Assert.assertEquals(7, status.getNbInstances());
		Assert.assertEquals(10.75, status.getTotalCpu(), 0.0001);
		Assert.assertEquals(45576, status.getTotalRam());
		Assert.assertEquals(6, status.getNbPublicAccess());
		Assert.assertEquals(7, status.getNbStorages()); // 3*2 (server1) + 1
		Assert.assertEquals(175, status.getTotalStorage());
	}

	@Test
	public void getSusbcriptionStatusEmpty() {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(getSubscription("mda", ProvResource.SERVICE_KEY));
		Assert.assertEquals("quote2", status.getName());
		Assert.assertEquals("quoteD2", status.getDescription());
		Assert.assertNotNull(status.getId());
		checkCost0(status.getCost());
		Assert.assertEquals(0, status.getNbInstances());
		Assert.assertEquals(0, status.getTotalCpu(), 0.0001);
		Assert.assertEquals(0, status.getTotalRam());
		Assert.assertEquals(0, status.getNbStorages());
		Assert.assertEquals(0, status.getTotalStorage());
	}

	@Test
	public void getConfiguration() {
		QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote1", vo.getName());
		Assert.assertEquals("quoteD1", vo.getDescription());
		checkCost(vo.getCost(), 4692.785, 7139.185, false);
		checkCost(resource.refreshCost(subscription), 4692.785, 7139.185, false);
		vo = resource.getConfiguration(subscription);
		checkCost(vo.getCost(), 4692.785, 7139.185, false);

		Assert.assertNotNull(vo.getId());
		Assert.assertNotNull(vo.getCreatedBy());
		Assert.assertNotNull(vo.getCreatedDate());
		Assert.assertNotNull(vo.getLastModifiedBy());
		Assert.assertNotNull(vo.getLastModifiedDate());
		Assert.assertEquals("region-1", vo.getLocation());

		// Check compute
		final List<ProvQuoteInstance> instances = vo.getInstances();
		Assert.assertEquals(7, instances.size());
		final ProvQuoteInstance quoteInstance = instances.get(0);
		Assert.assertNotNull(quoteInstance.getId());
		Assert.assertEquals("server1", quoteInstance.getName());
		Assert.assertEquals("serverD1", quoteInstance.getDescription());
		Assert.assertTrue(quoteInstance.getConstant());
		Assert.assertEquals(InternetAccess.PUBLIC, quoteInstance.getInternet());
		Assert.assertEquals(10.1, quoteInstance.getMaxVariableCost(), DELTA);
		Assert.assertEquals(2, quoteInstance.getMinQuantity().intValue());
		Assert.assertEquals(10, quoteInstance.getMaxQuantity().intValue());
		final ProvInstancePrice instancePrice = quoteInstance.getPrice();
		Assert.assertEquals(0.2, instancePrice.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, instancePrice.getOs());
		Assert.assertNotNull(instancePrice.getTerm().getId());
		Assert.assertFalse(instancePrice.getTerm().isEphemeral());
		Assert.assertFalse(instancePrice.getTerm().isVariable());
		Assert.assertEquals(15, instancePrice.getTerm().getPeriod().intValue());
		Assert.assertEquals(60, instancePrice.getTerm().getMinimum().intValue());
		Assert.assertEquals("on-demand1", instancePrice.getTerm().getName());
		Assert.assertEquals("15 minutes fragment", instancePrice.getTerm().getDescription());
		final ProvInstanceType instance = instancePrice.getType();
		Assert.assertNotNull(instance.getId().intValue());
		Assert.assertEquals("instance1", instance.getName());
		Assert.assertEquals("instanceD1", instance.getDescription());
		Assert.assertEquals(0.5, instance.getCpu(), 0.0001);
		Assert.assertEquals(2000, instance.getRam().intValue());
		Assert.assertTrue(instance.getConstant());

		// No minimal for this instance price
		Assert.assertNull(instances.get(1).getPrice().getTerm().getMinimum());
		Assert.assertNull(instances.get(1).getMaxVariableCost());

		Assert.assertEquals(1, instances.get(3).getMinQuantity().intValue());
		Assert.assertEquals(1, instances.get(3).getMaxQuantity().intValue());

		// Check the constant CPU requirement
		Assert.assertTrue(instances.get(0).getConstant());
		Assert.assertNull(instances.get(1).getConstant());
		Assert.assertFalse(instances.get(3).getConstant());

		// Check the network requirement
		Assert.assertEquals(InternetAccess.PUBLIC, instances.get(0).getInternet());
		Assert.assertEquals(InternetAccess.PRIVATE, instances.get(1).getInternet());
		Assert.assertEquals(InternetAccess.PRIVATE_NAT, instances.get(2).getInternet());

		// Check storage
		final List<ProvQuoteStorage> storages = vo.getStorages();
		Assert.assertEquals(4, storages.size());
		final ProvQuoteStorage quoteStorage = storages.get(0);
		Assert.assertNotNull(quoteStorage.getId());
		Assert.assertEquals("server1-root", quoteStorage.getName());
		Assert.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assert.assertEquals(20, quoteStorage.getSize().intValue());
		Assert.assertEquals(8.4, quoteStorage.getCost(), DELTA);
		Assert.assertEquals(42, quoteStorage.getMaxCost(), DELTA); // = 8.4 * 5
		Assert.assertNotNull(quoteStorage.getQuoteInstance());
		final ProvStoragePrice storage = quoteStorage.getPrice();
		final ProvStorageType storageType = storage.getType();
		Assert.assertNotNull(storage.getId());
		Assert.assertEquals(0.21, storage.getCostGb(), DELTA);
		Assert.assertEquals(0, storage.getCost(), DELTA);
		Assert.assertEquals("storage1", storageType.getName());
		Assert.assertEquals("storageD1", storageType.getDescription());
		Assert.assertEquals(0, storage.getCostTransaction(), DELTA);
		Assert.assertEquals(ProvStorageFrequency.HOT, storageType.getFrequency());
		Assert.assertEquals(ProvStorageOptimized.IOPS, storageType.getOptimized());

		// Not attached storage
		Assert.assertNull(storages.get(3).getQuoteInstance());

		// Check the small transactional cost
		Assert.assertEquals(0.000000072, storages.get(1).getPrice().getCostTransaction(), 0.000000001);

		// Check the related instance and price for the next comparison
		Assert.assertEquals("instance1", instances.get(0).getPrice().getType().getName());
		Assert.assertEquals("instance1", instances.get(1).getPrice().getType().getName());
		Assert.assertEquals("instance1", instances.get(2).getPrice().getType().getName());
		Assert.assertEquals("instance3", instances.get(3).getPrice().getType().getName());
		Assert.assertEquals("instance5", instances.get(4).getPrice().getType().getName());
		Assert.assertEquals("instance10", instances.get(5).getPrice().getType().getName());
		Assert.assertEquals("dynamic", instances.get(6).getPrice().getType().getName());

		Assert.assertEquals("on-demand1", instances.get(0).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand2", instances.get(1).getPrice().getTerm().getName());
		Assert.assertEquals("1y", instances.get(2).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances.get(3).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand2", instances.get(4).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances.get(5).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances.get(6).getPrice().getTerm().getName());

		// Optimize the configuration
		checkCost(resource.refreshCostAndResource(subscription), 3307.63, 5754.03, false);

		final QuoteVo vo2 = resource.getConfiguration(subscription);
		Assert.assertEquals("quote1", vo2.getName());
		Assert.assertEquals(vo2.getId(), vo.getId());

		// Check the new instances
		final List<ProvQuoteInstance> instances2 = vo2.getInstances();
		Assert.assertEquals(7, instances2.size());

		// Same instance
		Assert.assertEquals("instance1", instances2.get(0).getPrice().getType().getName());
		Assert.assertEquals("dynamic", instances2.get(5).getPrice().getType().getName());

		// Fixed instance types for the same constraints
		Assert.assertEquals("instance2", instances2.get(1).getPrice().getType().getName());
		Assert.assertEquals("instance2", instances2.get(2).getPrice().getType().getName());
		Assert.assertEquals("instance2", instances2.get(3).getPrice().getType().getName());
		Assert.assertEquals("instance6", instances2.get(4).getPrice().getType().getName());
		Assert.assertEquals("dynamic", instances2.get(6).getPrice().getType().getName());

		// Check the contracts are the same
		Assert.assertEquals("on-demand1", instances2.get(0).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand2", instances2.get(1).getPrice().getTerm().getName());
		Assert.assertEquals("1y", instances2.get(2).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances2.get(3).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand2", instances2.get(4).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances2.get(5).getPrice().getTerm().getName());
		Assert.assertEquals("on-demand1", instances2.get(6).getPrice().getTerm().getName());

	}

	@Test
	public void getConfigurationEmpty() {
		checkCost0(resource.refreshCost(checkEmpty()));
		checkCost0(resource.refreshCostAndResource(checkEmpty()));
	}

	private int checkEmpty() {
		final int subscription = getSubscription("mda", ProvResource.SERVICE_KEY);
		final QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote2", vo.getName());
		Assert.assertEquals("quoteD2", vo.getDescription());
		Assert.assertNotNull(vo.getId());
		checkCost0(vo.getCost());

		// Check compute
		Assert.assertEquals(0, vo.getInstances().size());

		// Check storage
		Assert.assertEquals(0, vo.getStorages().size());
		return subscription;
	}

	private void checkCost0(final FloatingCost cost) {
		checkCost(cost, 0, 0, false);
	}

	private QuoteLigthVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assert.assertEquals(min, cost.getMin(), DELTA);
		Assert.assertEquals(max, cost.getMax(), DELTA);
		Assert.assertEquals(unbound, cost.isUnbound());
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupInstance() {
		final QuoteInstanceLookup lookup = resource.lookupInstance(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, null);
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location.
	 */
	@Test
	public void lookupInstanceLocation() {
		final QuoteInstanceLookup lookup = resource.lookupInstance(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-1");
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location different from the quote's
	 * one.
	 */
	@Test
	public void lookupInstanceLocationNotFoundButWorldwideService() {
		final QuoteInstanceLookup lookup = resource.lookupInstance(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-xxx");
		checkInstance(lookup);
	}

	/**
	 * Search instance type within a region where minimal instance types are not
	 * available.
	 */
	@Test
	public void lookupInstanceLocationNotFound() {
		Assert.assertEquals("instance2", resource.lookupInstance(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-xxx")
				.getPrice().getType().getName());

		final ProvLocation location = locationRepository.findByName("region-1");

		// Add location constraint on the first matching instances to exclude them
		ipRepository.findAllBy("type.name", "instance2").forEach(ip -> ip.setLocation(location));
		ipRepository.findAllBy("type.name", "dynamic").forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		// Instance 2 is not available in this region
		Assert.assertEquals("instance4", resource.lookupInstance(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-xxx")
				.getPrice().getType().getName());
	}

	/**
	 * Update the location of the quote, impact all instances, but no one use the
	 * default location. Cost still updated.
	 */
	@Test
	public void updateLocation() {
		final ProvLocation location4 = locationRepository.findByName("region-4");

		// Make sure there is no more world wild prices
		em.createQuery("FROM ProvInstancePrice WHERE location IS NULL", ProvInstancePrice.class).getResultList()
				.forEach(ip -> ip.setLocation(location4));
		em.flush();
		em.clear();

		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-1");
		final FloatingCost cost = resource.update(subscription, quote);
		checkCost(cost, 7167.333, 11030.704, false);
		ProvQuote quote2 = repository.findByNameExpected("name1");
		Assert.assertEquals("description1", quote2.getDescription());
		Assert.assertEquals("region-1", quote2.getLocation().getName());

		// CHeck the association on the quote
		Assert.assertEquals("region-1", resource.getConfiguration(subscription).getLocation());

		// Check the "region-1" is the one related to our provider
		Assert.assertEquals("service:prov:test", repository.findByName("name1").getLocation().getNode().getId());
	}

	/**
	 * Update the location of the quote, impact all instances, but no one use the
	 * default location. Cost still updated.
	 */
	@Test
	public void updateLocationDifferentQILocation() {
		final ProvLocation location = locationRepository.findByName("region-1");
		final ProvLocation location4 = locationRepository.findByName("region-4");

		// Change the required location of all quote instance
		qiRepository.findAll().forEach(ip -> ip.setLocation(location));

		// Make sure there is no more world wild prices
		em.createQuery("FROM ProvInstancePrice WHERE location.name=:location", ProvInstancePrice.class).setParameter("location", "region-1")
				.getResultList().forEach(ip -> ip.setLocation(location4));
		em.createQuery("FROM ProvStoragePrice WHERE location.name=:location", ProvStoragePrice.class).setParameter("location", "region-1")
				.getResultList().forEach(ip -> ip.setLocation(location4));
		em.createQuery("FROM ProvQuoteInstance WHERE location.name=:location", ProvQuoteInstance.class).setParameter("location", "region-1")
				.getResultList().forEach(ip -> ip.setLocation(location4));
		em.flush();
		em.clear();

		// New cost based on region-4
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-4");
		final FloatingCost cost = resource.update(subscription, quote);
		checkCost(cost, 3307.63, 5754.03, false);
		ProvQuote quote2 = repository.findByNameExpected("name1");
		Assert.assertEquals("description1", quote2.getDescription());
		Assert.assertEquals("region-4", quote2.getLocation().getName());
	}

	/**
	 * Update the location of the quote, impact some storages. And block some
	 * storages associated to instances not located on the same location.
	 */
	@Test(expected = ValidationJsonException.class)
	public void updateLocationKoDifferentStorageAndQILocation() {
		final ProvLocation location = locationRepository.findByName("region-1");

		// Change the required location of all quote instance
		qiRepository.findAll().forEach(ip -> ip.setLocation(location));

		// Make sure there is no more world wild prices for instance, but keep the
		// existing global storage locations
		em.createQuery("FROM ProvInstancePrice WHERE location IS NULL", ProvInstancePrice.class).getResultList()
				.forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-2");
		resource.update(subscription, quote);
	}

	/**
	 * Update to an unknown location.
	 */
	@Test(expected = EntityNotFoundException.class)
	public void updateLocationNotExists() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-x");
		resource.update(subscription, quote);
	}

	/**
	 * Update the location related to another provider.
	 */
	@Test(expected = EntityNotFoundException.class)
	public void updateLocationNotExistsForThisSubscription() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-3");
		resource.update(subscription, quote);
	}

	private void checkInstance(final QuoteInstanceLookup lookup) {
		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance2", pi.getType().getName());
		Assert.assertEquals(1, pi.getType().getCpu().intValue());
		Assert.assertEquals(2000, pi.getType().getRam().intValue());
		Assert.assertFalse(pi.getTerm().isEphemeral());
		Assert.assertEquals(0.14, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("1y", pi.getTerm().getName());
		Assert.assertEquals(102.2, lookup.getCost(), DELTA);
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupInstanceHighContraints() throws IOException {
		final QuoteInstanceLookup lookup = new ObjectMapperTrim()
				.readValue(new ObjectMapperTrim().writeValueAsString(resource.lookupInstance(subscription, 3, 9, true, VmOs.WINDOWS, null,
						iptRepository.findByNameExpected("on-demand1").getId(), false, null)), QuoteInstanceLookup.class);
		final ProvInstancePrice pi = lookup.getPrice();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance9", pi.getType().getName());
		Assert.assertEquals(4, pi.getType().getCpu().intValue());
		Assert.assertEquals(16000, pi.getType().getRam().intValue());
		Assert.assertTrue(pi.getType().getConstant());
		Assert.assertEquals(5.6, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assert.assertEquals("on-demand1", pi.getTerm().getName());
		Assert.assertFalse(pi.getTerm().isEphemeral());
		Assert.assertFalse(pi.getType().isCustom());

		// Not serialized
		Assert.assertNull(pi.getType().getNode());
		Assert.assertNull(pi.getTerm().getNode());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceNoMatch() {
		Assert.assertNull(resource.lookupInstance(subscription, 999, 0, false, VmOs.SUSE, null,
				iptRepository.findByNameExpected("1y").getId(), true, null));
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceOnlyCustom() {
		final QuoteInstanceLookup lookup = resource.lookupInstance(subscription, 999, 0, null, VmOs.LINUX, null, null, true, null);

		// Check the custom instance
		final ProvInstancePrice pi = lookup.getPrice();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("dynamic", pi.getType().getName());
		Assert.assertEquals(0, pi.getType().getCpu().intValue());
		Assert.assertEquals(0, pi.getType().getRam().intValue());
		Assert.assertTrue(pi.getType().getConstant());
		Assert.assertEquals(0, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("on-demand1", pi.getTerm().getName());
		Assert.assertTrue(pi.getType().isCustom());

		Assert.assertEquals(241928.03, lookup.getCost(), DELTA);
	}

	@Test
	public void getKey() {
		Assert.assertEquals("service:prov", resource.getKey());

		// Only there for coverage of associations required by JPA
		new ProvQuote().setStorages(null);
		new ProvQuote().getStorages();
		new ProvQuote().setInstances(null);
		new ProvQuoteInstance().setStorages(null);
		ProvStorageFrequency.valueOf(ProvStorageFrequency.HOT.name());
		ProvStorageOptimized.valueOf(ProvStorageOptimized.IOPS.name());
		VmOs.valueOf(VmOs.LINUX.name());
		ProvTenancy.valueOf(ProvTenancy.DEDICATED.name());
	}

	/**
	 * Attempt to attach a storage incompatible to an instance.
	 */
	@Test(expected = ValidationJsonException.class)
	public void createStorageInstanceKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(1);
		resource.createStorage(vo);
	}

	/**
	 * Attempt to attach a storage compatible to an instance but without an
	 * instance.
	 */
	@Test
	public void createStorageNoInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setSize(1);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4694.935, 7141.335, false);
		checkCost(cost.getResourceCost(), 2.15, 2.15, false);
		Assert.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4694.935, 7141.335, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("storage3-root-bis", storage.getName());
		Assert.assertEquals(1, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assert.assertEquals(2.15, storage.getCost(), DELTA);
		Assert.assertFalse(storage.isUnboundCost());
	}

	@Test
	public void createStorageInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4907.825, 8214.385, false);
		checkCost(cost.getResourceCost(), 215.04, 1075.2, false);
		Assert.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292, 1460, false);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4907.825, 8214.385, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assert.assertEquals(215.04, storage.getCost(), DELTA);
		Assert.assertFalse(storage.isUnboundCost());
	}

	@Test
	public void createStorageUnboundInstance() {
		final ProvQuoteInstance quoteInstance = setUnboundInstance("server1");

		// Attach the new storage to this unbound instance
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(quoteInstance.getId());
		vo.setSize(512);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4907.825, 4907.825, true);
		checkCost(cost.getResourceCost(), 215.04, 215.04, true);
		Assert.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292, 292, true);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4907.825, 4907.825, true);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assert.assertEquals(215.04, storage.getCost(), DELTA);
		Assert.assertTrue(storage.isUnboundCost());
	}

	/**
	 * Change the given instance name from bound to unbound type.
	 */
	private ProvQuoteInstance setUnboundInstance(final String name) {
		final ProvQuoteInstance quoteInstance = qiRepository.findByNameExpected(name);

		// Precondition
		Assert.assertEquals(10, quoteInstance.getMaxQuantity().intValue());
		Assert.assertEquals(0, quoteInstance.getConfiguration().getUnboundCostCounter().intValue());

		// Make the instance with unbound cost
		quoteInstance.setMaxQuantity(null);
		quoteInstance.getConfiguration().setUnboundCostCounter(1);
		em.flush();
		em.clear();
		resource.refreshCost(subscription);
		em.flush();
		return quoteInstance;
	}

	@Test
	public void createStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setDescription("server1-root-terD");
		vo.setType("storage1");
		vo.setSize(256);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4746.545, 7192.945, false);
		checkCost(cost.getResourceCost(), 53.76, 53.76, false);
		Assert.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4746.545, 7192.945, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-ter", storage.getName());
		Assert.assertEquals("server1-root-terD", storage.getDescription());
		Assert.assertEquals(256, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assert.assertEquals(53.76, storage.getCost(), DELTA);
		Assert.assertFalse(storage.isUnboundCost());
	}

	@Test(expected = EntityNotFoundException.class)
	public void createStorageNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setType("storage1");
		vo.setSize(1);
		resource.createStorage(vo);
	}

	@Test
	public void updateStorageDetachAttach() {
		checkCost(subscription, 4692.785, 7139.185, false);

		// Make "server1" with unbound maximal quantities
		setUnboundInstance("server1");

		// Check the new cost corresponds to the minimal cost since there is
		// only
		// one unbound instance
		checkCost(subscription, 4692.785, 4692.785, true);

		// Detach "server1-root" storage from "server1"
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setSize(20);

		// Check the new cost is equals to :
		// NEW_TOTAL .. = OLD_TOTAL-(STORAGE.COST*(STORAGE.INSTANCE.MIN-1))
		// ............ = OLD_TOTAL-STORAGE.COST * (2 -1))
		// ............ = OLD_TOTAL-STORAGE.COST
		// STORAGE_COST = STORAGE_COST * STORAGE.INSTANCE.MIN
		// ............ = 4.2 * 1
		UpdatedCost cost = resource.updateStorage(vo);
		checkCost(cost.getTotalCost(), 4688.585, 4688.585, true);
		checkCost(cost.getResourceCost(), 4.2, 4.2, false);
		Assert.assertEquals(0, cost.getRelatedCosts().size());

		// Check the exact new cost
		checkCost(subscription, 4688.585, 4688.585, true);
		final ProvQuoteStorage qs = qsRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-root-bis", qs.getName());
		Assert.assertFalse(qs.isUnboundCost());

		// Attach back this storage to "server1
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		cost = resource.updateStorage(vo);
		checkCost(cost.getTotalCost(), 4692.785, 4692.785, true);
		checkCost(cost.getResourceCost(), 8.4, 8.4, true);
		Assert.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292, 292, true);

		checkCost(subscription, 4692.785, 4692.785, true);
		Assert.assertTrue(qsRepository.findOneExpected(vo.getId()).isUnboundCost());
	}

	@Test
	public void updateStorageNoLimit() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		vo.setLocation("region-1");
		resource.updateStorage(vo);

		// Check the exact new cost
		checkCost(subscription, 4899.425, 8172.385, false);
		Assert.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateStorageInvalidLocation() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		vo.setLocation("region-Z");
		resource.updateStorage(vo);
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateStorageNotVisibleLocation() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		vo.setLocation("region-3");
		resource.updateStorage(vo);
	}

	@Test
	public void updateStorageLimit() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setDescription("server1-root-bisD");

		// Change the storage type -> storage2 has a minimal to 512
		vo.setType("storage2");
		vo.setSize(512); // Limit for this storage is 512
		resource.updateStorage(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4762.185, 7174.985, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals("server1-root-bisD", storage.getDescription());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assert.assertEquals(77.8, storage.getCost(), DELTA);
	}

	@Test(expected = ValidationJsonException.class)
	public void updateStorageLimitKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage2");
		vo.setSize(1024); // Limit for this storage is 512
		resource.updateStorage(vo);
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateStorageUnknownStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage-unknown");
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	/**
	 * Selected storage is not within the same provider.
	 */
	@Test(expected = EntityNotFoundException.class)
	public void updateStorageInvalidStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storageX");
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void updateStorageUnknownInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(-1);
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	/**
	 * Selected instance is not within the same provider.
	 */
	@Test(expected = EntityNotFoundException.class)
	public void updateStorageNonVisibleInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("serverX").getId());
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateInstanceNonVisibleInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findBy("type.name", "instanceX").getId());
		vo.setName("server1-bis");
		vo.setRam(1);
		vo.setCpu(0.5);
		resource.updateInstance(vo);
	}

	@Test
	public void delete() {
		// Check the pre-deletion
		Assert.assertEquals(3, repository.findAll().size());

		em.flush();
		em.clear();

		resource.delete(subscription, true);
		em.flush();
		em.clear();

		// Check the post-deletion
		Assert.assertEquals(2, repository.findAll().size());
	}

	@Test
	public void deleteAllInstances() {
		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assert.assertTrue(qsRepository.existsById(storage1));
		Assert.assertTrue(qsRepository.existsById(storage2));
		Assert.assertTrue(qsRepository.existsById(storage3));
		Assert.assertEquals(8, qiRepository.count());
		em.flush();
		em.clear();

		checkCost(resource.deleteAllInstances(subscription), 2.73, 2.73, false);

		// Check the exact new cost
		checkCost(subscription, 2.73, 2.73, false);
		Assert.assertNull(qiRepository.findOne(id));
		Assert.assertEquals(1, qiRepository.count());

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.existsById(storage1));
		Assert.assertFalse(qsRepository.existsById(storage2));
		Assert.assertFalse(qsRepository.existsById(storage3));
		Assert.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	public void deleteInstance() {
		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assert.assertTrue(qsRepository.existsById(storage1));
		Assert.assertTrue(qsRepository.existsById(storage2));
		Assert.assertTrue(qsRepository.existsById(storage3));
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		em.flush();
		em.clear();

		checkCost(resource.deleteInstance(id), 4081.185, 4081.185, false);

		// Check the exact new cost
		checkCost(subscription, 4081.185, 4081.185, false);
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assert.assertNull(qiRepository.findOne(id));

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.existsById(storage1));
		Assert.assertFalse(qsRepository.existsById(storage2));
		Assert.assertFalse(qsRepository.existsById(storage3));
		Assert.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	public void deleteUnboundInstance() {
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMaxQuantity(null);
		final int id = resource.createInstance(vo).getId();

		// Check the counter is now 1
		Assert.assertEquals(1, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		em.flush();
		em.clear();

		resource.deleteInstance(id);

		// Check the counter is back to 0
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
	}

	@Test
	public void deleteAllStorages() {
		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assert.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		Assert.assertEquals(8, qiRepository.count());
		Assert.assertEquals(4, qsRepository.count());
		em.flush();
		em.clear();

		checkCost(resource.deleteAllStorages(subscription), 4370.455, 5538.455, false);

		// Check the exact new cost
		checkCost(subscription, 4370.455, 5538.455, false);

		// Check the associations
		Assert.assertNull(qsRepository.findOne(id));
		Assert.assertEquals(0, qiRepository.findByNameExpected("server1").getStorages().size());

		Assert.assertEquals(8, qiRepository.count());
		Assert.assertEquals(0, qsRepository.count());
	}

	@Test
	public void deleteStorage() {
		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assert.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		em.flush();
		em.clear();

		checkCost(resource.deleteStorage(id), 4684.385, 7097.185, false);

		// Check the exact new cost
		checkCost(subscription, 4684.385, 7097.185, false);

		// Check the associations
		Assert.assertNull(qsRepository.findOne(id));
		Assert.assertEquals(2, qiRepository.findByNameExpected("server1").getStorages().size());
	}

	private void refreshCost() {

		// Check the cost fully updated and exact actual cost
		final FloatingCost cost = resource.refreshCost(subscription);
		Assert.assertEquals(4692.785, cost.getMin(), DELTA);
		Assert.assertEquals(7139.185, cost.getMax(), DELTA);
		Assert.assertFalse(cost.isUnbound());
		checkCost(subscription, 4692.785, 7139.185, false);
		em.flush();
		em.clear();
	}

	private Map<Integer, FloatingCost> toStoragesFloatingCost(final String instanceName) {
		return qsRepository.findAllBy("quoteInstance.name", instanceName).stream().collect(Collectors.toMap(ProvQuoteStorage::getId,
				qs -> new FloatingCost(qs.getCost(), qs.getMaxCost(), qs.getQuoteInstance().getMaxQuantity() == null)));
	}

	@Test
	public void testToString() {
		final QuoteInstanceLookup computedInstancePrice = new QuoteInstanceLookup();
		computedInstancePrice.setCost(1.23);
		final ProvInstancePrice ip = new ProvInstancePrice();
		final ProvInstancePriceTerm type = new ProvInstancePriceTerm();
		type.setName("type1");
		ip.setTerm(type);
		final ProvInstanceType instance = new ProvInstanceType();
		instance.setName("instance1");
		ip.setType(instance);
		computedInstancePrice.setPrice(ip);
		Assert.assertTrue(computedInstancePrice.toString().contains("cost=1.23"));
		Assert.assertTrue(computedInstancePrice.toString().contains("name=instance1"));

		final QuoteStorageLoopup computedStoragePrice = new QuoteStorageLoopup();
		computedStoragePrice.setCost(1.23);
		final ProvStoragePrice sp = new ProvStoragePrice();
		final ProvStorageType stype = new ProvStorageType();
		stype.setName("type1");
		sp.setType(stype);
		computedStoragePrice.setPrice(sp);
		Assert.assertTrue(computedStoragePrice.toString().contains("cost=1.23"));
		Assert.assertTrue(computedStoragePrice.toString().contains("name=type1"));
	}

	@Test
	public void findLocations() {
		final TableItem<ProvLocation> locations = resource.findLocations(subscription, newUriInfo());
		Assert.assertEquals(3, locations.getData().size());
		Assert.assertEquals("region-1", locations.getData().get(0).getName());
		Assert.assertEquals("region-2", locations.getData().get(1).getName());
		Assert.assertEquals("region-4", locations.getData().get(2).getName());
		Assert.assertEquals("service:prov:test", locations.getData().get(2).getNode().getId());
	}

	@Test
	public void updateInstanceIdentity() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assert.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.2).getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		final UpdatedCost updatedCost = resource.updateInstance(vo);
		Assert.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4692.785, 7139.185, false);
		checkCost(updatedCost.getResourceCost(), 292, 1460, false);

		// Check the related storage prices
		Assert.assertEquals(3, updatedCost.getRelatedCosts().size());

		// Check the cost is the same
		refreshCost();
	}

	@Test
	public void updateInstanceUnbound() {
		ProvQuoteStorage qs = qsRepository.findByNameExpected("server1-root");
		Assert.assertFalse(qs.isUnboundCost());
		Assert.assertEquals(8.4, qs.getCost(), DELTA);
		Assert.assertEquals(42, qs.getMaxCost(), DELTA);

		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assert.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.2).getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(null);
		UpdatedCost updatedCost = resource.updateInstance(vo);
		Assert.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4386.985, 4386.985, true);
		checkCost(updatedCost.getResourceCost(), 146, 146, true);
		checkCost(subscription, 4386.985, 4386.985, true);

		// Check the related storage prices
		Assert.assertEquals(3, updatedCost.getRelatedCosts().size());
		qs = qsRepository.findByNameExpected("server1-root");
		Assert.assertEquals(4.2, updatedCost.getRelatedCosts().get(qs.getId()).getMin(), DELTA);
		Assert.assertEquals(4.2, updatedCost.getRelatedCosts().get(qs.getId()).getMax(), DELTA);
		Assert.assertTrue(updatedCost.getRelatedCosts().get(qs.getId()).isUnbound());
		Assert.assertTrue(qs.isUnboundCost());
		Assert.assertEquals(4.2, qs.getCost(), DELTA);
		Assert.assertEquals(4.2, qs.getMaxCost(), DELTA);

		// Check the cost is the same
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		updatedCost = resource.updateInstance(vo);
		refreshCost();
	}

	@Test(expected = ValidationJsonException.class)
	public void updateInstanceIncommatibleOs() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setOs(VmOs.CENTOS);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		resource.updateInstance(vo);
	}

	@Test
	public void updateInstance() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assert.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		vo.setLocation("region-1");
		final UpdatedCost updatedCost = resource.updateInstance(vo);
		Assert.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4449.035, 11438.185, false);
		checkCost(updatedCost.getResourceCost(), 208.05, 4161, false);
		checkCost(subscription, 4449.035, 11438.185, false);

		// Check the related storage prices
		Assert.assertEquals(3, updatedCost.getRelatedCosts().size());

		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-bis", instance.getName());
		Assert.assertEquals(1024, instance.getRam().intValue());
		Assert.assertEquals(0.5, instance.getCpu(), DELTA);
		Assert.assertEquals(208.05, instance.getCost(), DELTA);
		Assert.assertEquals(4161, instance.getMaxCost(), DELTA);
		Assert.assertEquals("region-1", instance.getLocation().getName());
	}

	@Test
	public void updateInstanceOsCompatible() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server2").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.16).getId());
		vo.setName("server2-bis");
		vo.setOs(VmOs.CENTOS);
		vo.setRam(1024);
		vo.setCpu(0.5);
		resource.updateInstance(vo);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assert.assertEquals(VmOs.CENTOS, instance.getOs());
	}

	@Test
	public void createInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setConstant(true);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setMaxVariableCost(210.9);
		vo.setEphemeral(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		final UpdatedCost updatedCost = resource.createInstance(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6773.285, 10259.935, false);
		checkCost(updatedCost.getResourceCost(), 2080.5, 3120.75, false);
		Assert.assertTrue(updatedCost.getRelatedCosts().isEmpty());
		checkCost(subscription, 6773.285, 10259.935, false);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assert.assertEquals("serverZ", instance.getName());
		Assert.assertTrue(instance.isEphemeral());
		Assert.assertEquals("serverZD", instance.getDescription());
		Assert.assertEquals(1024, instance.getRam().intValue());
		Assert.assertEquals(0.5, instance.getCpu(), DELTA);
		Assert.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assert.assertEquals(2080.5, instance.getCost(), DELTA);
		Assert.assertEquals(3120.75, instance.getMaxCost(), DELTA);
		Assert.assertTrue(instance.getConstant());
		Assert.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assert.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assert.assertEquals(10, instance.getMinQuantity().intValue());
		Assert.assertEquals(15, instance.getMaxQuantity().intValue());
		Assert.assertFalse(instance.isUnboundCost());
	}

	@Test(expected = ValidationJsonException.class)
	public void createInstanceIncompatibleOs() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setOs(VmOs.SUSE);
		vo.setConstant(true);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setMaxVariableCost(210.9);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		resource.createInstance(vo);
	}

	@Test
	public void createUnboundInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(null);
		final UpdatedCost updatedCost = resource.createInstance(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6773.285, 9219.685, true);
		checkCost(updatedCost.getResourceCost(), 2080.5, 2080.5, true);
		Assert.assertTrue(updatedCost.getRelatedCosts().isEmpty());
		checkCost(subscription, 6773.285, 9219.685, true);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assert.assertNull(instance.getMaxVariableCost());
		Assert.assertEquals(10, instance.getMinQuantity().intValue());
		Assert.assertNull(instance.getMaxQuantity());
		Assert.assertTrue(instance.isUnboundCost());
	}

	@Test(expected = ValidationJsonException.class)
	public void createInstanceMinGreaterThanMax() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(100);
		vo.setMaxQuantity(10);
		resource.createInstance(vo);
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		final AbstractProvResource res = new AbstractProvResource() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		};
		res.provResource = resource;
		final QuoteLigthVo quote = (QuoteLigthVo) res.checkSubscriptionStatus(subscription, null, Collections.emptyMap()).getData()
				.get("quote");
		Assert.assertNotNull(quote);
		checkCost(quote.getCost(), 4692.785, 7139.185, false);
	}

	@Test
	public void getInstalledEntities() {
		Assert.assertTrue(new AbstractProvResource() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		}.getInstalledEntities().contains(ProvStorageType.class));
	}

	@Test
	public void updateCatalog() {

	}

	@Test
	public void create() throws Exception {
		final Subscription subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		em.flush();
		em.clear();
		resource.create(subscription.getId());
		final QuoteVo configuration = resource.getConfiguration(subscription.getId());
		Assert.assertNotNull(configuration);
		Assert.assertNotNull(configuration.getName());
		Assert.assertNotNull(configuration.getDescription());
	}

	@Test(expected = BusinessException.class)
	public void createNoCatalog() throws Exception {
		final Subscription subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		qiRepository.deleteAll();
		qsRepository.deleteAll();
		repository.deleteAll();
		ipRepository.deleteAll();
		iptRepository.deleteAll();
		itRepository.deleteAll();
		spRepository.deleteAll();
		stRepository.deleteAll();
		locationRepository.deleteAll();
		em.flush();
		em.clear();
		resource.create(subscription.getId());
	}

	@Test
	public void findInstanceTerm() {
		final TableItem<ProvInstancePriceTerm> tableItem = resource.findInstancePriceTerm(subscription, newUriInfo());
		Assert.assertEquals(3, tableItem.getRecordsTotal());
		Assert.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTermCriteria() {
		final TableItem<ProvInstancePriceTerm> tableItem = resource.findInstancePriceTerm(subscription, newUriInfo("deMand"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
		Assert.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void findInstancePriceTermNotExistsSubscription() {
		resource.findInstancePriceTerm(-1, newUriInfo());
	}

	@Test
	public void findInstancePriceTermAnotherSubscription() {
		Assert.assertEquals(1, resource.findInstancePriceTerm(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test(expected = EntityNotFoundException.class)
	public void findInstancePriceTermNotVisibleSubscription() {
		initSpringSecurityContext("any");
		resource.findInstancePriceTerm(subscription, newUriInfo());
	}

	@Test
	public void findStorageType() {
		final TableItem<ProvStorageType> tableItem = resource.findStorageType(subscription, newUriInfo());
		Assert.assertEquals(3, tableItem.getRecordsTotal());
		Assert.assertEquals("storage1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findStorageTypeCriteria() {
		final TableItem<ProvStorageType> tableItem = resource.findStorageType(subscription, newUriInfo("rAge2"));
		Assert.assertEquals(1, tableItem.getRecordsTotal());
		Assert.assertEquals("storage2", tableItem.getData().get(0).getName());
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void findStorageTypeNotExistsSubscription() {
		resource.findStorageType(-1, newUriInfo());
	}

	@Test
	public void findStorageTypeAnotherSubscription() {
		Assert.assertEquals(1, resource.findStorageType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test(expected = EntityNotFoundException.class)
	public void findStorageTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		resource.findStorageType(subscription, newUriInfo());
	}

	@Test
	public void findInstance() {
		final TableItem<ProvInstanceType> tableItem = resource.findInstance(subscription, newUriInfo());
		Assert.assertEquals(13, tableItem.getRecordsTotal());
		Assert.assertEquals("instance1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstanceCriteria() {
		final TableItem<ProvInstanceType> tableItem = resource.findInstance(subscription, newUriInfo("sTance1"));
		Assert.assertEquals(4, tableItem.getRecordsTotal());
		Assert.assertEquals("instance1", tableItem.getData().get(0).getName());
		Assert.assertEquals("instance10", tableItem.getData().get(1).getName());
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void findInstanceNotExistsSubscription() {
		resource.findInstance(-1, newUriInfo());
	}

	@Test
	public void findInstanceAnotherSubscription() {
		Assert.assertEquals(1, resource.findInstance(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test(expected = EntityNotFoundException.class)
	public void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		resource.findInstance(subscription, newUriInfo());
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupStorage() {
		final QuoteStorageLoopup price = resource.lookupStorage(subscription, 2, null, null, ProvStorageOptimized.IOPS, null).get(0);

		// Check the storage result
		assertCSP(price);
		Assert.assertEquals(0.42, price.getCost(), DELTA);
		Assert.assertEquals(2, price.getSize());
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupStorageHighContraints() throws IOException {
		final QuoteStorageLoopup lookup = resource.lookupStorage(subscription, 1024, ProvStorageFrequency.HOT, null, null, null).get(0);
		final String asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assert.assertTrue(asJson.startsWith("{\"cost\":215.04,\"price\":{\"id\":"));
		Assert.assertTrue(asJson.contains("\"cost\":0.0,\"location\":\"region-1\",\"type\":{\"id\":"));
		Assert.assertTrue(asJson.endsWith(
				"\"name\":\"storage1\",\"description\":\"storageD1\",\"frequency\":\"hot\",\"optimized\":\"iops\",\"minimal\":1,\"maximal\":null,\"instanceCompatible\":true},\"costGb\":0.21,\"costTransaction\":0.0},\"size\":1024}"));

		// Check the storage result
		assertCSP(lookup);
		Assert.assertEquals(215.04, lookup.getCost(), DELTA);
	}

	private QuoteStorageLoopup assertCSP(final QuoteStorageLoopup price) {
		final ProvStoragePrice sp = price.getPrice();
		final ProvStorageType st = sp.getType();
		Assert.assertNotNull(sp.getId());
		Assert.assertNotNull(st.getId());
		Assert.assertEquals("storage1", st.getName());
		return price;
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupStorageNoMatch() {
		Assert.assertFalse(resource.lookupStorage(subscription, 512, ProvStorageFrequency.HOT, null, null, null).isEmpty());
		Assert.assertFalse(resource.lookupStorage(subscription, 999, ProvStorageFrequency.HOT, null, null, null).isEmpty());
		Assert.assertFalse(resource.lookupStorage(subscription, 512, ProvStorageFrequency.COLD, null, null, null).isEmpty());

		// Out of limits
		Assert.assertTrue(resource.lookupStorage(subscription, 999, ProvStorageFrequency.COLD, null, null, null).isEmpty());
	}

	@Test
	public void upload() throws IOException {
		resource.upload(subscription, new ClassPathResource("csv/upload.csv").getInputStream(),
				new String[] { "name", "cpu", "ram", "disk", "frequency", "os", "constant" }, null, 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(18, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assert.assertEquals(15, configuration.getStorages().size());
		Assert.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14917.079, 17363.479, false);
	}

	@Test
	public void uploadDefaultHeader() throws IOException {
		resource.upload(subscription, new ClassPathResource("csv/upload-default.csv").getInputStream(), null, null, 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(18, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assert.assertEquals(1, configuration.getInstances().get(17).getMinQuantity().intValue());
		Assert.assertEquals(1, configuration.getInstances().get(17).getMaxQuantity().intValue());
		Assert.assertNull(configuration.getInstances().get(17).getMaxVariableCost());
		Assert.assertEquals("dynamic", configuration.getInstances().get(12).getPrice().getType().getName());
		Assert.assertEquals(14, configuration.getStorages().size());
		Assert.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14839.279, 17285.679, false);
	}

	@Test
	public void uploadDefaultPriceTerm() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;true;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "constant", "ephemeral" }, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvInstancePrice instancePrice = configuration.getInstances().get(7).getPrice();
		final ProvInstancePriceTerm ipt = instancePrice.getTerm();
		Assert.assertEquals("on-demand2", ipt.getName());
		Assert.assertTrue(ipt.isEphemeral());
		Assert.assertTrue(ipt.isVariable());
		Assert.assertEquals("instance1", instancePrice.getType().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.535, 7266.935, false);
	}

	@Test
	public void uploadFixedInstance() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "ephemeral" }, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvInstancePriceTerm term = configuration.getInstances().get(7).getPrice().getTerm();
		Assert.assertEquals("on-demand2", term.getName());
		Assert.assertEquals("instance10", configuration.getInstances().get(7).getPrice().getType().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadBoundQuantities() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1000;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" }, "on-demand2", 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assert.assertEquals(1, qi.getMinQuantity().intValue());
		Assert.assertTrue(qi.getPrice().getTerm().isEphemeral());
		Assert.assertTrue(qi.getPrice().getTerm().isVariable());
		Assert.assertEquals(1000, qi.getMaxQuantity().intValue());
		Assert.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 135099.185, false);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assert.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 210, false);
	}

	@Test
	public void uploadMaxQuantities() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" }, "on-demand2", 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assert.assertEquals(1, qi.getMinQuantity().intValue());
		Assert.assertEquals(1, qi.getMaxQuantity().intValue());
		Assert.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 7267.145, false);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assert.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, false);
	}

	@Test
	public void uploadUnBoundQuantities() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;0;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" }, "on-demand2", 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assert.assertEquals(1, qi.getMinQuantity().intValue());
		Assert.assertNull(qi.getMaxQuantity());
		Assert.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 7267.145, true);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assert.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, true);
	}

	@Test
	public void uploadInternetAccess() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;PUBLIC;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "internet", "ephemeral" }, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("instance10", configuration.getInstances().get(7).getPrice().getType().getName());
		Assert.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadFixedPriceTerm() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;on-demand1;66".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "term", "maxVariableCost" }, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance instance = configuration.getInstances().get(7);
		final ProvInstancePrice instancePrice = instance.getPrice();
		Assert.assertEquals("on-demand1", instancePrice.getTerm().getName());
		Assert.assertFalse(instancePrice.getTerm().isEphemeral());
		Assert.assertFalse(instancePrice.getTerm().isVariable());
		Assert.assertEquals("instance2", instancePrice.getType().getName());
		Assert.assertEquals(66, instance.getMaxVariableCost(), DELTA);
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4827.835, 7274.235, false);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;LINUX".getBytes("UTF-8")), null, null, 1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 246640.288, 249086.688, false);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;1;64;LINUX".getBytes("UTF-8")), null, null, 1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 5142.672, 7589.072, false);
	}

	/**
	 * Expected location does not exist for this subscription, so there is no
	 * matching instance
	 */
	@Test(expected = ValidationJsonException.class)
	public void uploadInvalidLocationForSubscription() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;region-3".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "location" }, "on-demand2", 1, "UTF-8");
	}

	/**
	 * Expected location does not exist at all
	 */
	@Test(expected = ValidationJsonException.class)
	public void uploadInvalidLocation() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;region-ZZ".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "location" }, "on-demand2", 1, "UTF-8");
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadInstanceNotFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;WINDOWS".getBytes("UTF-8")), null, "on-demand1", 1024, "UTF-8");
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadStorageNotFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;1;1;LINUX;99999999999;HOT;THROUGHPUT".getBytes("UTF-8")), null,
				"on-demand1", 1, "UTF-8");
	}

	@Test(expected = BusinessException.class)
	public void uploadInvalidHeader() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY".getBytes("UTF-8")), new String[] { "any" }, "on-demand1", 1, "UTF-8");
	}

	@Test
	public void update() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-1");
		final FloatingCost cost = resource.update(subscription, quote);
		checkCost(cost, 3307.63, 5754.03, false);
		ProvQuote quote2 = repository.findByNameExpected("name1");
		Assert.assertEquals("description1", quote2.getDescription());
		Assert.assertEquals("region-1", quote2.getLocation().getName());
	}
}
