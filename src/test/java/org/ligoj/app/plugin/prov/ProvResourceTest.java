package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

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
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
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
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
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
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
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
	private ImportCatalogStatusRepository icsRepository;

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
		Assert.assertEquals("region-1", status.getLocation());
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

		// No associated usage for this use case
		Assert.assertNull(vo.getUsage());

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
		final ProvQuote quote = repository.findByNameExpected(status.getName());
		Assert.assertSame(unbound, quote.isUnboundCost());
		Assert.assertSame(quote, quote.getConfiguration());
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assert.assertEquals(min, cost.getMin(), DELTA);
		Assert.assertEquals(max, cost.getMax(), DELTA);
		Assert.assertEquals(unbound, cost.isUnbound());
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
		final ProvQuote quote2 = repository.findByNameExpected("name1");
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
		resource.create(subscription.getId());
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
