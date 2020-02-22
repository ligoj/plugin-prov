/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.database;

import static org.ligoj.app.plugin.prov.quote.database.QuoteDatabaseQuery.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteDatabaseResource}
 */
public class ProvQuoteDatabaseResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteDatabaseResource qbResource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvDatabasePriceRepository bpRepository;

	@Override
	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class,
				ProvCurrency.class, ProvQuote.class, ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class,
				ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		clearAllCache();
		configuration.put(ProvResource.USE_PARALLEL, "0");
		updateCost();
	}

	@Test
	void getConfigurationTest() {
		final var databases = resource.getConfiguration(subscription).getDatabases();
		Assertions.assertEquals(7, databases.size());
	}

	@Test
	void refresh() {
		final var refresh = resource.refresh(subscription);
		checkCost(refresh, 5613.6, 8209.5, false);
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookup() {
		final var lookup = qbResource.lookup(subscription,
				builder().usage("Full Time 12 month").engine("MYSQL").build());
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	void lookupLicenseIncluded() {
		final var lookup = qbResource.lookup(subscription,
				builder().usage("Full Time 12 month").license("INCLUDED").engine("MYSQL").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("database2", pi.getType().getName());
		Assertions.assertEquals("MYSQL3", pi.getCode());
		Assertions.assertEquals("MYSQL", pi.getEngine());
		Assertions.assertNull(pi.getStorageEngine());
		Assertions.assertNull(pi.getEdition());
		Assertions.assertNull(pi.getLicense());

		// Coverage only
		Assertions.assertTrue(pi.toString().contains("engine=MYSQL, edition=null"));
		Assertions.assertTrue(lookup.toString().contains("engine=MYSQL, edition=null"));
		new ProvQuoteDatabase().setStorages(null);
		Assertions.assertNotNull(qbResource.getItRepository());
	}

	/**
	 * Builder coverage
	 */
	@Test
	void queryJson() throws IOException {
		new ObjectMapperTrim().readValue("{\"engine\":\"MYSQL\",\"edition\":\"EDITION\","
				+ "\"cpu\":2,\"ram\":3000,\"constant\":true,\"license\":\"LI\""
				+ ",\"location\":\"L\",\"usage\":\"U\",\"type\":\"T\"}", QuoteDatabaseQuery.class);
		builder().toString();
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	void lookupLicenseByol() {
		final var lookup = qbResource.lookup(subscription, builder().cpu(0.5).ram(2000).usage("Full Time 12 month")
				.license("BYOL").engine("ORACLE").edition("ENTERPRISE").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("database1", pi.getType().getName());
		Assertions.assertEquals("ORACLE6", pi.getCode());
		Assertions.assertEquals("ORACLE", pi.getEngine());
		Assertions.assertEquals("ENTERPRISE", pi.getEdition());
		Assertions.assertEquals("BYOL", pi.getLicense());
		Assertions.assertEquals("ORACLE", pi.getStorageEngine());
	}

	/**
	 * Basic case, almost no requirements but processor.
	 */
	@Test
	void lookupProcessor() {
		final var lookup = qbResource.lookup(subscription, builder().engine("MYSQL").processor("Intel").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("database2", pi.getType().getName());
		Assertions.assertEquals("MYSQL3", pi.getCode());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", pi.getType().getProcessor());
	}

	/**
	 * Basic case, almost no requirements but location.
	 */
	@Test
	void lookupLocation() {
		final var lookup = qbResource.lookup(subscription,
				builder().location("region-1").usage("Full Time 12 month").engine("MYSQL").build());
		checkInstance(lookup);
	}

	/**
	 * Search instance type within a non existing region
	 */
	@Test
	void lookupLocationNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qbResource.lookup(subscription, builder().location("region-xxx").engine("MYSQL").build()));
	}

	private void checkInstance(final QuoteDatabaseLookup lookup) {
		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("database2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu().intValue());
		Assertions.assertEquals(2000, pi.getType().getRam().intValue());
		Assertions.assertEquals("MYSQL3", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(168.0, pi.getCost(), DELTA);
		Assertions.assertEquals(2034.0, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals("MYSQL", pi.getEngine());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(168.0, lookup.getCost(), DELTA);
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	void lookupHighConstraints() {
		final var lookup = qbResource.lookup(subscription,
				builder().cpu(0.25).ram(1900).constant(true).usage("Full Time 12 month").engine("MYSQL").build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("database1", pi.getType().getName());
		Assertions.assertEquals(0.5, pi.getType().getCpu(), DELTA);
		Assertions.assertEquals(2000, pi.getType().getRam().intValue());
		Assertions.assertTrue(pi.getType().getConstant());
		Assertions.assertEquals(89.5, pi.getCost(), DELTA);
		Assertions.assertEquals("MYSQL", pi.getEngine());
		Assertions.assertEquals("1y", pi.getTerm().getName());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertFalse(pi.getType().isCustom());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupNoMatch() {
		Assertions.assertNull(qbResource.lookup(subscription, builder().cpu(999).engine("MYSQL").build()));
	}

	/**
	 * No match edition
	 */
	@Test
	void lookupNoMatchEngine() {
		Assertions.assertNull(qbResource.lookup(subscription, builder().engine("any").build()));
	}

	/**
	 * No match edition
	 */
	@Test
	void lookupNoMatchEdition() {
		Assertions.assertNull(qbResource.lookup(subscription, builder().engine("MYSQL").edition("any").build()));
	}

	@Test
	void deleteAll() {
		final var id = qbRepository.findByNameExpected("database1").getId();
		final var storage1 = qsRepository.findByNameExpected("database1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(8, qbRepository.count());
		em.flush();
		em.clear();

		// After delete, it remains only the unattached storages and non database instances
		checkCost(qbResource.deleteAll(subscription), 4704.758, 7154.358, false);

		// Check the exact new cost
		checkCost(subscription, 4704.758, 7154.358, false);
		Assertions.assertNull(qbRepository.findOne(id));
		Assertions.assertEquals(0, qbRepository.findAll(subscription).size());

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	void deleteAllWithSupport() throws IOException {
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class, ProvQuoteSupport.class },
				StandardCharsets.UTF_8.name());
		qsRepository.deleteAllBy("name", "shared-data");
		resource.refresh(subscription);
		checkCost(subscription, 6113.414, 8839.109, false);
		em.flush();
		em.clear();

		// There is only quote instance with support
		checkCost(qbResource.deleteAll(subscription), 3500.937, 6114.884, false);
		checkCost(resource.getConfiguration(subscription).getCostNoSupport(), 3162.67, 5612.27, false);
		checkCost(resource.getConfiguration(subscription).getCostSupport(), 338.267, 502.614, false);
		checkCost(subscription, 3500.937, 6114.884, false);
		Assertions.assertEquals(0, qbRepository.findAll(subscription).size());
	}

	@Test
	void delete() {
		final var id = qbRepository.findByNameExpected("database1").getId();
		final var storage1 = qsRepository.findByNameExpected("database1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertFalse(repository.findBy("subscription.id", subscription).isUnboundCost());

		em.flush();
		em.clear();

		checkCost(qbResource.delete(id), 6958.898, 9408.498, false);

		// Check the exact new cost
		checkCost(subscription, 6958.898, 9408.498, false);
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertNull(qbRepository.findOne(id));

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	private Map<Integer, FloatingCost> toStoragesFloatingCost(final String instanceName) {
		return qsRepository.findAllBy("quoteDatabase.name", instanceName).stream().collect(Collectors.toMap(
				ProvQuoteStorage::getId,
				qs -> new FloatingCost(qs.getCost(), qs.getMaxCost(), qs.getQuoteDatabase().getMaxQuantity() == null)));
	}

	@Test
	void updateIdentity() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("database1");
		Assertions.assertEquals(1, storagePrices.size());

		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qbRepository.findByNameExpected("database1").getId());
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("database1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setEngine("MYSQL");
		vo.setMinQuantity(1);
		vo.setMaxQuantity(2);
		final var updatedCost = qbResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotal(), 7105.198, 9701.098, false);
		checkCost(updatedCost.getCost(), 116.3, 232.6, false);

		// Check the related storage prices: only one attached database storage
		Assertions.assertEquals(1, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		// Check the cost is the same
		updateCost();
	}

	@Test
	void updateIncompatibleEngine() {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qbRepository.findByNameExpected("database1").getId());
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("database1-bis");
		vo.setRam(1024);
		vo.setEngine("ORACLE");
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qbResource.update(vo)),
				"engine", "incompatible-engine");
	}

	@Test
	void update() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("database1");
		Assertions.assertEquals(1, storagePrices.size());

		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qbRepository.findByNameExpected("database1").getId());
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("database1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		vo.setLocation("region-1");
		vo.setUsage("Full Time");
		vo.setEngine("MYSQL");
		final var updatedCost = qbResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 7105.198, 12334.498, false);
		checkCost(updatedCost.getCost(), 116.3, 2326.0, false);
		checkCost(subscription, 7105.198, 12334.498, false);

		// Check the related storage prices: only one attached storage
		Assertions.assertEquals(1, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		final var instance = qbRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("database1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(116.3, instance.getCost(), DELTA);
		Assertions.assertEquals(2326.0, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		final var updatedCost2 = qbResource.update(vo);
		checkCost(updatedCost2.getTotal(), 7047.048, 11171.498, false);
		checkCost(updatedCost2.getCost(), 58.15, 1163.0, false);

		// Change the region of this instance, storage is also
		vo.setLocation("region-2");
	}

	@Test
	void updateLocationNoMatchStorage() {
		// Add a storage only available in "region-1"
		final var qs = new ProvQuoteStorage();
		qs.setPrice(spRepository.findBy("type.name", "storage4"));
		qs.setLatency(Rate.BEST);
		qs.setQuoteDatabase(qbRepository.findByName("database1"));
		qs.setName("qi-storage4");
		qs.setConfiguration(repository.findByName("quote1"));
		qs.setCost(0d);
		qs.setMaxCost(0d);
		qs.setSize(100);
		em.persist(qs);
		em.flush();

		// Check the cost
		checkCost(resource.refresh(subscription), 5755.6, 8493.5, false);

		// Everything identity but the region
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qbRepository.findByNameExpected("database1").getId());
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("database1");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		vo.setEngine("MYSQL");
		vo.setLocation("region-1");

		// No change
		checkCost(qbResource.update(vo).getTotal(), 6043.9, 10799.9, false);

		vo.setLocation("region-2"); // "region-1" to "region-2"
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qbResource.update(vo)),
				"storage", "no-match-storage");
	}

	@Test
	void create() {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(bpRepository.findByExpected("code", "ORACLE1").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setConstant(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		vo.setEngine("ORACLE");
		vo.setEdition("STANDARD ONE");
		final var updatedCost = qbResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 8569.198, 11897.098, false);
		checkCost(updatedCost.getCost(), 1464.0, 2196.0, false);
		Assertions.assertEquals(1, updatedCost.getRelated().size());
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).isEmpty());
		checkCost(subscription, 8569.198, 11897.098, false);
		final var instance = qbRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals("ORACLE", instance.getEngine());
		Assertions.assertEquals("STANDARD ONE", instance.getEdition());
		Assertions.assertEquals(1464.0, instance.getCost(), DELTA);
		Assertions.assertEquals(2196.0, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());
	}

	@Test
	void createProcessor() {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL3").getId());
		vo.setName("serverZ");
		vo.setEngine("MYSQL");
		vo.setProcessor("Intel");
		final var updatedCost = qbResource.create(vo);

		checkCost(updatedCost.getCost(), 168.0, 168.0, true);
		final var instance = qbRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("MYSQL", instance.getEngine());
		Assertions.assertEquals("Intel", instance.getProcessor());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", instance.getPrice().getType().getProcessor());
	}

	@Test
	void createIncompatibleEngine() {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setEngine("ANY");
		vo.setConstant(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qbResource.create(vo)),
				"engine", "incompatible-engine");
	}

	@Test
	void createIncompatibleEdition() {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(bpRepository.findByExpected("code", "MYSQL1").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setEngine("MYSQL");
		vo.setEdition("ANY");
		vo.setConstant(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qbResource.create(vo)),
				"edition", "incompatible-edition");
	}

	@Test
	void findInstanceTerms() {
		final var tableItem = qbResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsCriteria() {
		final var tableItem = qbResource.findPriceTerms(subscription, newUriInfo("deMand"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qbResource.findPriceTerms(-1, newUriInfo()));
	}

	@Test
	void findInstancePriceTermsAnotherSubscription() {
		Assertions.assertEquals(1,
				qbResource.findPriceTerms(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qbResource.findPriceTerms(subscription, newUriInfo()));
	}

	@Test
	void findLicenses() {
		final var tableItem = qbResource.findLicenses(subscription, "ORACLE");
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("INCLUDED", tableItem.get(0));
		Assertions.assertEquals("BYOL", tableItem.get(1));
	}

	@Test
	void findEngine() {
		final var tableItem = qbResource.findEngines(subscription);
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("MYSQL", tableItem.get(0));
		Assertions.assertEquals("ORACLE", tableItem.get(1));
	}

	@Test
	void findEdition() {
		final var tableItem = qbResource.findEditions(subscription, "ORACLE");
		Assertions.assertEquals(3, tableItem.size());
		Assertions.assertEquals("ENTERPRISE", tableItem.get(0));
		Assertions.assertEquals("STANDARD ONE", tableItem.get(1));
	}

	@Test
	void findEditionNone() {
		Assertions.assertEquals(0, qbResource.findEditions(subscription, "mysql").size());
	}

	@Test
	void findLicensesNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qbResource.findLicenses(subscription, "ORACLE"));
	}

	@Test
	void findEditionsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qbResource.findEditions(subscription, "ORACLE"));
	}

	@Test
	void findAllTypes() {
		final var tableItem = qbResource.findAllTypes(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("database1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceCriteria() {
		final var tableItem = qbResource.findAllTypes(subscription, newUriInfo("base1"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("database1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qbResource.findAllTypes(-1, newUriInfo()));
	}

	@Test
	void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qbResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qbResource.findAllTypes(subscription, newUriInfo()));
	}

	@Override
	protected void updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(7105.198, cost.getMin(), DELTA);
		Assertions.assertEquals(9701.098, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 7105.198, 9701.098, false);
		em.flush();
		em.clear();
	}

	@Test
	void getSubscriptionStatus() {
		final var status = resource.getSubscriptionStatus(subscription);
		Assertions.assertEquals("quote1", status.getName());
		Assertions.assertEquals("quoteD1", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost(status.getCost(), 7105.198, 9701.098, false);
		Assertions.assertEquals(7, status.getNbInstances());
		Assertions.assertEquals(7, status.getNbDatabases());
		Assertions.assertEquals(13.75, status.getTotalCpu(), 0.0001); // 10.75 + 3 (DB)
		Assertions.assertEquals(57976, status.getTotalRam());
		Assertions.assertEquals(13, status.getNbPublicAccess());
		Assertions.assertEquals(8, status.getNbStorages()); // 3*2 (server1) + 1 + 1 DB
		Assertions.assertEquals(195, status.getTotalStorage()); // 175 + 20 (DB)
		Assertions.assertEquals("region-1", status.getLocation().getName());
	}
}
