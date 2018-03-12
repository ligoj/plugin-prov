package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.MatcherUtil;
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
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvResource}
 */
@ExtendWith(SpringExtension.class)
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

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class,
				ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
				ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		updateCost();
	}

	@Test
	public void testBusiness() {
		// Coverage only
		Assertions.assertEquals(InternetAccess.PUBLIC.ordinal(),
				InternetAccess.valueOf(InternetAccess.values()[0].name()).ordinal());

		// Association only
		Assertions.assertEquals("service:prov:test",
				stRepository.findBy("node.id", "service:prov:test").getNode().getId());
	}

	@Test
	public void getSusbcriptionStatus() {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		Assertions.assertEquals("quote1", status.getName());
		Assertions.assertEquals("quoteD1", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost(status.getCost(), 4704.758, 7154.358, false);
		Assertions.assertEquals(7, status.getNbInstances());
		Assertions.assertEquals(10.75, status.getTotalCpu(), 0.0001);
		Assertions.assertEquals(45576, status.getTotalRam());
		Assertions.assertEquals(6, status.getNbPublicAccess());
		Assertions.assertEquals(7, status.getNbStorages()); // 3*2 (server1) + 1
		Assertions.assertEquals(175, status.getTotalStorage());
		Assertions.assertEquals("region-1", status.getLocation().getName());
	}

	@Test
	public void getSusbcriptionStatusEmpty() {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(getSubscription("mda", ProvResource.SERVICE_KEY));
		Assertions.assertEquals("quote2", status.getName());
		Assertions.assertEquals("quoteD2", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost0(status.getCost());
		Assertions.assertEquals(0, status.getNbInstances());
		Assertions.assertEquals(0, status.getTotalCpu(), 0.0001);
		Assertions.assertEquals(0, status.getTotalRam());
		Assertions.assertEquals(0, status.getNbStorages());
		Assertions.assertEquals(0, status.getTotalStorage());
	}

	@Test
	public void getConfiguration() {
		QuoteVo vo = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote1", vo.getName());
		Assertions.assertEquals("quoteD1", vo.getDescription());
		checkCost(vo.getCost(), 4704.758, 7154.358, false);
		checkCost(resource.updateCost(subscription), 4704.758, 7154.358, false);
		vo = resource.getConfiguration(subscription);
		checkCost(vo.getCost(), 4704.758, 7154.358, false);

		Assertions.assertNotNull(vo.getId());
		Assertions.assertNotNull(vo.getCreatedBy());
		Assertions.assertNotNull(vo.getCreatedDate());
		Assertions.assertNotNull(vo.getLastModifiedBy());
		Assertions.assertNotNull(vo.getLastModifiedDate());
		Assertions.assertEquals("region-1", vo.getLocation().getName());

		// Check compute
		final List<ProvQuoteInstance> instances = vo.getInstances();
		Assertions.assertEquals(7, instances.size());
		final ProvQuoteInstance quoteInstance = instances.get(0);
		Assertions.assertNotNull(quoteInstance.getId());
		Assertions.assertEquals("server1", quoteInstance.getName());
		Assertions.assertEquals("serverD1", quoteInstance.getDescription());
		Assertions.assertTrue(quoteInstance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, quoteInstance.getInternet());
		Assertions.assertEquals(10.1, quoteInstance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(2, quoteInstance.getMinQuantity().intValue());
		Assertions.assertEquals(10, quoteInstance.getMaxQuantity().intValue());
		final ProvInstancePrice price = quoteInstance.getPrice();
		Assertions.assertEquals(146.4, price.getCost(), DELTA);
		Assertions.assertEquals(146.4, price.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, price.getOs());
		Assertions.assertNotNull(price.getTerm().getId());
		Assertions.assertFalse(price.getTerm().isEphemeral());
		Assertions.assertFalse(price.getTerm().isVariable());
		Assertions.assertEquals(0, price.getTerm().getPeriod());
		Assertions.assertEquals("on-demand1", price.getTerm().getName());
		Assertions.assertEquals("15 minutes fragment", price.getTerm().getDescription());
		final ProvInstanceType instance = price.getType();
		Assertions.assertNotNull(instance.getId().intValue());
		Assertions.assertEquals("instance1", instance.getName());
		Assertions.assertEquals("instanceD1", instance.getDescription());
		Assertions.assertEquals(0.5, instance.getCpu(), 0.0001);
		Assertions.assertEquals(2000, instance.getRam().intValue());
		Assertions.assertTrue(instance.getConstant());

		// No minimal for this instance price
		Assertions.assertNull(instances.get(1).getMaxVariableCost());

		Assertions.assertEquals(1, instances.get(3).getMinQuantity().intValue());
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
		final List<ProvQuoteStorage> storages = vo.getStorages();
		Assertions.assertEquals(4, storages.size());
		final ProvQuoteStorage quoteStorage = storages.get(0);
		Assertions.assertNotNull(quoteStorage.getId());
		Assertions.assertEquals("server1-root", quoteStorage.getName());
		Assertions.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assertions.assertEquals(20, quoteStorage.getSize().intValue());
		Assertions.assertEquals(8.4, quoteStorage.getCost(), DELTA);
		Assertions.assertEquals(42, quoteStorage.getMaxCost(), DELTA); // = 8.4 * 5
		Assertions.assertNotNull(quoteStorage.getQuoteInstance());
		final ProvStoragePrice storage = quoteStorage.getPrice();
		final ProvStorageType storageType = storage.getType();
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

		// Optimize the configuration
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		final QuoteVo vo2 = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote1", vo2.getName());
		Assertions.assertEquals(vo2.getId(), vo.getId());

		// Check the new instances
		final List<ProvQuoteInstance> instances2 = vo2.getInstances();
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
	}

	@Test
	public void getConfigurationEmpty() {
		checkCost0(resource.updateCost(checkEmpty()));
		checkCost0(resource.refresh(checkEmpty()));
	}

	private int checkEmpty() {
		final int subscription = getSubscription("mda", ProvResource.SERVICE_KEY);
		final QuoteVo vo = resource.getConfiguration(subscription);
		Assertions.assertEquals("quote2", vo.getName());
		Assertions.assertEquals("quoteD2", vo.getDescription());
		Assertions.assertNotNull(vo.getId());
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

	private QuoteLigthVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		final ProvQuote quote = repository.findByNameExpected(status.getName());
		Assertions.assertSame(unbound, quote.isUnboundCost());
		Assertions.assertSame(quote, quote.getConfiguration());
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
	}

	/**
	 * Update the location of the quote, impact all instances, but no one use the default location. Cost still updated.
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
		checkCost(cost, 5799.465, 9669.918, false);
		ProvQuote quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());

		// Check location
		final ProvLocation location = quote2.getLocation();
		Assertions.assertEquals("region-1", location.getName());
		Assertions.assertEquals("west", location.getPlacement());
		Assertions.assertEquals("840", location.getCountryM49());
		Assertions.assertEquals("021", location.getRegionM49());
		Assertions.assertEquals("019", location.getContinentM49());

		// CHeck the association on the quote
		Assertions.assertEquals("region-1", resource.getConfiguration(subscription).getLocation().getName());

		// Check the "region-1" is the one related to our provider
		Assertions.assertEquals("service:prov:test", repository.findByName("name1").getLocation().getNode().getId());
	}

	/**
	 * Update the location of the quote, impact all instances, but no one use the default location. Cost still updated.
	 */
	@Test
	public void updateLocationDifferentQILocation() {
		final ProvLocation location = locationRepository.findByName("region-1");
		final ProvLocation location4 = locationRepository.findByName("region-4");

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

		// New cost based on region-4
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-4");
		final FloatingCost cost = resource.update(subscription, quote);
		checkCost(cost, 3165.4, 5615.0, false);
		final ProvQuote quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());
		Assertions.assertEquals("region-4", quote2.getLocation().getName());
	}

	/**
	 * Update the location of the quote, impact some storages. And block some storages associated to instances not
	 * located on the same location.
	 */
	@Test
	public void updateLocationKoDifferentStorageAndQILocation() {
		final ProvLocation location = locationRepository.findByName("region-1");

		// Change the required location of all quote instance
		qiRepository.findAll().forEach(ip -> ip.setLocation(location));

		// Make sure there is no more world wild prices for instance, but keep
		// the
		// existing global storage locations
		em.createQuery("FROM ProvInstancePrice WHERE location IS NULL", ProvInstancePrice.class).getResultList()
				.forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-2");
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			resource.update(subscription, quote);
		}), "storage", "no-match-storage");
	}

	/**
	 * Update to an unknown location.
	 */
	@Test
	public void updateLocationNotExists() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-x");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.update(subscription, quote);
		});
	}

	/**
	 * Update the location related to another provider.
	 */
	@Test
	public void updateLocationNotExistsForThisSubscription() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-3");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.update(subscription, quote);
		});
	}

	@Test
	public void getKey() {
		Assertions.assertEquals("service:prov", resource.getKey());

		// Only there for coverage of associations required by JPA
		new ProvQuote().setStorages(null);
		new ProvQuote().getStorages();
		new ProvQuote().setInstances(null);
		new ProvQuoteInstance().setStorages(null);
		Rate.valueOf(Rate.GOOD.name());
		ProvStorageOptimized.valueOf(ProvStorageOptimized.IOPS.name());
		VmOs.valueOf(VmOs.LINUX.name());
		ProvTenancy.valueOf(ProvTenancy.DEDICATED.name());
	}

	@Test
	public void delete() {
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
	public void deleteNoConfiguration() {
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

	private void updateCost() {

		// Check the cost fully updated and exact actual cost
		final FloatingCost cost = resource.updateCost(subscription);
		Assertions.assertEquals(4704.758, cost.getMin(), DELTA);
		Assertions.assertEquals(7154.358, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4704.758, 7154.358, false);
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
		Assertions.assertTrue(computedInstancePrice.toString().contains("cost=1.23"));
		Assertions.assertTrue(computedInstancePrice.toString().contains("name=instance1"));

		final QuoteStorageLoopup computedStoragePrice = new QuoteStorageLoopup();
		computedStoragePrice.setCost(1.23);
		final ProvStoragePrice sp = new ProvStoragePrice();
		final ProvStorageType stype = new ProvStorageType();
		stype.setName("type1");
		sp.setType(stype);
		computedStoragePrice.setPrice(sp);
		Assertions.assertTrue(computedStoragePrice.toString().contains("cost=1.23"));
		Assertions.assertTrue(computedStoragePrice.toString().contains("name=type1"));
	}

	@Test
	public void findLocations() {
		final TableItem<ProvLocation> locations = resource.findLocations(subscription, newUriInfo());
		Assertions.assertEquals(3, locations.getData().size());
		Assertions.assertEquals("region-1", locations.getData().get(0).getName());
		Assertions.assertEquals("region-2", locations.getData().get(1).getName());
		Assertions.assertEquals("region-4", locations.getData().get(2).getName());
		Assertions.assertEquals("service:prov:test", locations.getData().get(2).getNode().getId());
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
		final QuoteLigthVo quote = (QuoteLigthVo) res
				.checkSubscriptionStatus(subscription, null, Collections.emptyMap()).getData().get("quote");
		Assertions.assertNotNull(quote);
		checkCost(quote.getCost(), 4704.758, 7154.358, false);
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
		Assertions.assertNotNull(configuration);
		Assertions.assertNotNull(configuration.getName());
		Assertions.assertNotNull(configuration.getDescription());
	}

	@Test
	public void createNoCatalog() {
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
		Assertions.assertEquals("service:prov-no-catalog", Assertions.assertThrows(BusinessException.class, () -> {
			resource.create(subscription.getId());
		}).getMessage());
	}

	@Test
	public void update() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("name1");
		quote.setDescription("description1");
		quote.setLocation("region-1");
		final FloatingCost cost = resource.update(subscription, quote);
		checkCost(cost, 3165.4, 5615.0, false);
		ProvQuote quote2 = repository.findByNameExpected("name1");
		Assertions.assertEquals("description1", quote2.getDescription());
		Assertions.assertEquals("region-1", quote2.getLocation().getName());
	}

	@Test
	public void findConfigured() {
		final ProvQuoteInstance qi = qiRepository.findByName("server1");
		Assertions.assertEquals("server1", resource.findConfigured(qiRepository, qi.getId(), subscription).getName());
	}

	@Test
	public void findConfiguredByName() {
		Assertions.assertEquals("server1",
				resource.findConfiguredByName(qiRepository, "server1", subscription).getName());
	}

	@Test
	public void findConfiguredNotFound() {
		final ProvQuoteInstance qi = qiRepository.findByName("server1");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.findConfigured(qiRepository, qi.getId(), 0).getName();
		});
	}

	@Test
	public void findConfiguredByNameNotFoundInvalidName() {
		Assertions.assertEquals("serverAAAAA", Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.findConfiguredByName(qiRepository, "serverAAAAA", subscription);
		}).getMessage());
	}

	@Test
	public void findConfiguredByNameNotFoundInvalidSub() {
		Assertions.assertEquals("server1", Assertions.assertThrows(EntityNotFoundException.class, () -> {
			resource.findConfiguredByName(qiRepository, "server1", 0);
		}).getMessage());
	}
}
