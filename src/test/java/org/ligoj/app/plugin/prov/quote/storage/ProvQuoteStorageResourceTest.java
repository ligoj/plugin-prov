/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
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
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvResource}
 */
public class ProvQuoteStorageResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
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
		updateCost();
	}

	private int server1() {
		return qiRepository.findByName("server1").getId();
	}

	private int database1() {
		return qbRepository.findByName("database1").getId();
	}

	/**
	 * Attempt to attach a storage incompatible to an instance.
	 */
	@Test
	void createInstanceKo() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setQuoteInstance(server1());
		vo.setSize(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.create(vo)),
				"type", "type-incompatible-requirements");
	}

	/**
	 * Attempt to attach a storage to 2 instances.
	 */
	@Test
	void createMultipleAttachment() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setQuoteDatabase(database1());
		vo.setSize(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.create(vo)),
				"instance", "ambiguous-instance-database");
	}

	/**
	 * Attempt to attach a storage compatible to an instance but without an instance.
	 */
	@Test
	void createNoInstance() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setSize(1);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7107.348, 9703.248, false);
		checkCost(cost.getCost(), 2.15, 2.15, false);
		Assertions.assertEquals(0, cost.getRelated().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7107.348, 9703.248, false);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("storage3-root-bis", storage.getName());
		Assertions.assertEquals(1, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(2.15, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	@Test
	void createInstance() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setSize(512);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7320.238, 10776.298, false);
		checkCost(cost.getCost(), 215.04, 1075.2, false);
		Assertions.assertEquals(1, cost.getRelated().size());
		checkCost(cost.getRelated().get(ResourceType.INSTANCE).get(vo.getQuoteInstance()), 292.8, 1464.0, false);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7320.238, 10776.298, false);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals(512, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(215.04, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	@Test
	void createDatabase() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("mysql1");
		vo.setType("storage5-database");
		vo.setQuoteDatabase(database1());
		vo.setSize(512);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7823.998, 11138.698, false);
		checkCost(cost.getCost(), 718.8, 1437.6, false);
		Assertions.assertEquals(1, cost.getRelated().size());
		checkCost(cost.getRelated().get(ResourceType.DATABASE).get(vo.getQuoteDatabase()), 116.3, 232.6, false);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7823.998, 11138.698, false);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("mysql1", storage.getName());
		Assertions.assertEquals(512, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(718.8, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	/**
	 * Create a storage to a database type having a engine constraint.
	 */
	@Test
	void createDatabaseEngineConstraint() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("oracle1");
		vo.setType("storage7-database");
		vo.setQuoteDatabase(qbRepository.findByName("database3").getId());
		vo.setSize(512);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7926.398, 10522.298, false);
		checkCost(cost.getCost(), 821.2, 821.2, false);
		Assertions.assertEquals(1, cost.getRelated().size());
		checkCost(cost.getRelated().get(ResourceType.DATABASE).get(vo.getQuoteDatabase()), 146.4, 146.4, false);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7926.398, 10522.298, false);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("oracle1", storage.getName());
		Assertions.assertEquals(512, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(821.2, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	/**
	 * Create a storage to a database type having an incompatible engine constraint.
	 */
	@Test
	void createDatabaseEngineIncompatibleEngine1() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("mysqlOverOracle");
		vo.setType("storage7-database");
		vo.setQuoteDatabase(qbRepository.findByName("database1").getId());
		vo.setSize(512);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.create(vo)),
				"type", "type-incompatible-requirements");
	}

	/**
	 * Create a storage to a database type having an incompatible engine constraint.
	 */
	@Test
	void createDatabaseEngineIncompatibleEngine2() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("oracleOverMySQL");
		vo.setType("storage5-database");
		vo.setQuoteDatabase(qbRepository.findByName("database3").getId());
		vo.setSize(512);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.create(vo)),
				"type", "type-incompatible-requirements");
	}

	@Test
	void createUnboundInstance() {
		setUnboundInstance("server1");

		// Attach the new storage to this unbound instance
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setSize(512);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7320.238, 7466.538, true);
		checkCost(cost.getCost(), 215.04, 215.04, true);
		Assertions.assertEquals(1, cost.getRelated().size());
		checkCost(cost.getRelated().get(ResourceType.INSTANCE).get(vo.getQuoteInstance()), 292.8, 292.8, true);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7320.238, 7466.538, true);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals(512, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(215.04, storage.getCost(), DELTA);
		Assertions.assertTrue(storage.isUnboundCost());
	}

	/**
	 * Change the given instance name from bound to unbound type.
	 */
	private ProvQuoteInstance setUnboundInstance(final String name) {
		final var quoteInstance = qiRepository.findByNameExpected(name);

		// Precondition
		Assertions.assertEquals(10, quoteInstance.getMaxQuantity().intValue());
		Assertions.assertEquals(0, quoteInstance.getConfiguration().getUnboundCostCounter().intValue());

		// Make the instance with unbound cost
		quoteInstance.setMaxQuantity(null);
		quoteInstance.getConfiguration().setUnboundCostCounter(1);
		em.flush();
		em.clear();
		resource.updateCost(subscription);
		em.flush();
		return quoteInstance;
	}

	@Test
	void create() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setDescription("server1-root-terD");
		vo.setType("storage1");
		vo.setSize(256);
		final var cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 7158.958, 9754.858, false);
		checkCost(cost.getCost(), 53.76, 53.76, false);
		Assertions.assertEquals(0, cost.getRelated().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7158.958, 9754.858, false);
		final var storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-ter", storage.getName());
		Assertions.assertEquals("server1-root-terD", storage.getDescription());
		Assertions.assertEquals(ResourceType.STORAGE, storage.getResourceType());
		Assertions.assertEquals(256, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(53.76, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	@Test
	void refresh() {
		// Create with constraints
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server-new");
		vo.setType("storage1");
		vo.setOptimized(null);
		vo.setLatency(Rate.GOOD);
		vo.setQuoteInstance(qiRepository.findByName("server2").getId());
		vo.setSize(512);
		final var cost = qsResource.create(vo);
		checkCost(cost.getCost(), 107.52, 107.52, false);

		// No change
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 107.52, 107.52, false);
		Assertions.assertEquals("storage1", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change some constraints
		vo.setLatency(Rate.WORST);
		vo.setQuoteInstance(null);
		vo.setId(cost.getId());

		// Cost is the same since the type still match the constraints
		checkCost(qsResource.update(vo).getCost(), 107.52, 107.52, false);

		// The cost changed since a best type matches to the constraints
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 77.8, 77.8, false);
		Assertions.assertEquals("storage2", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change the price of "storage3" to make it as cheap as "storage2"
		final var price3 = spRepository.findBy("type.name", "storage3");
		price3.setCost(1);
		spRepository.saveAndFlush(price3);

		// Also, change the latency of "storage3" to "GOOD" class
		final var type3 = stRepository.findByName("storage3");
		type3.setLatency(Rate.GOOD);
		stRepository.saveAndFlush(type3);

		// Even if "storage2" and "storage3" have identical prices and match to
		// the
		// requirements, "storage3" offers a lowest latency.
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 77.8, 77.8, false);
		Assertions.assertEquals("storage3", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());
	}

	@Test
	void createNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setType("storage1");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.create(vo));
	}

	@Test
	void updateDetachAttach() {
		checkCost(subscription, 7105.198, 9701.098, false);

		// Make "server1" with unbound maximal quantities
		setUnboundInstance("server1");

		// Check the new cost corresponds to the minimal cost since there is
		// only one unbound instance
		checkCost(subscription, 7105.198, 7251.498, true);

		// Detach "server1-root" storage from "server1"
		final var vo = new QuoteStorageEditionVo();
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
		var cost = qsResource.update(vo);
		checkCost(cost.getTotal(), 7100.998, 7247.298, true);
		checkCost(cost.getCost(), 4.2, 4.2, false);
		Assertions.assertEquals(0, cost.getRelated().size());

		// Check the exact new cost
		checkCost(subscription, 7100.998, 7247.298, true);
		final var qs = qsRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-root-bis", qs.getName());
		Assertions.assertFalse(qs.isUnboundCost());

		// Attach back this storage to "server1
		vo.setQuoteInstance(server1());
		cost = qsResource.update(vo);
		checkCost(cost.getTotal(), 7105.198, 7251.498, true);
		checkCost(cost.getCost(), 8.4, 8.4, true);
		Assertions.assertEquals(1, cost.getRelated().size());
		checkCost(cost.getRelated().get(ResourceType.INSTANCE).get(server1()), 292.8, 292.8, true);

		checkCost(subscription, 7105.198, 7251.498, true);
		Assertions.assertTrue(qsRepository.findOneExpected(vo.getId()).isUnboundCost());
	}

	@Test
	void updateNoLimit() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setSize(512);
		vo.setLocation("region-1");
		qsResource.update(vo);

		// Check the exact new cost
		checkCost(subscription, 7311.838, 10734.298, false);
		Assertions.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
	}

	@Test
	void updateInvalidLocation() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setSize(512);
		vo.setLocation("region-Z");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.update(vo));
	}

	@Test
	void updateNotVisibleLocation() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(server1());
		vo.setSize(512);
		vo.setLocation("region-3");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.update(vo));
	}

	@Test
	void updateLimit() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setDescription("server1-root-bisD");

		// Change the storage type -> storage2 has a minimal to 512
		vo.setType("storage2");
		vo.setSize(512); // Limit for this storage is 512
		qsResource.update(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 7174.598, 9736.898, false);
		final var storage = qsRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals("server1-root-bisD", storage.getDescription());
		Assertions.assertEquals(512, storage.getSize());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(77.8, storage.getCost(), DELTA);
	}

	@Test
	void updateLimitKo() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage2");
		vo.setSize(1024); // Limit for this storage is 512
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.update(vo)),
				"type", "type-incompatible-requirements");
	}

	@Test
	void updateUnknownStorage() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage-unknown");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.update(vo));
	}

	/**
	 * Selected storage is not within the same provider.
	 */
	@Test
	void updateInvalidStorage() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storageX");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.update(vo));
	}

	@Test
	void updateUnknownInstance() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(0);
		vo.setSize(1);
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qsResource.update(vo));
	}

	/**
	 * Selected instance is not within the same provider.
	 */
	@Test
	void updateNonVisibleInstance() {
		final var vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByName("serverX").getId());
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.update(vo));
	}

	@Test
	void deleteAllStorages() {
		final var id = qsRepository.findByNameExpected("server1-root").getId();
		Assertions.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		Assertions.assertEquals(8, qiRepository.count());
		Assertions.assertEquals(5, qsRepository.count());
		em.flush();
		em.clear();

		checkCost(qsResource.deleteAll(subscription), 6752.868, 8040.368, false);

		// Check the exact new cost
		checkCost(subscription, 6752.868, 8040.368, false);

		// Check the associations
		Assertions.assertNull(qsRepository.findOne(id));
		Assertions.assertEquals(0, qiRepository.findByNameExpected("server1").getStorages().size());

		Assertions.assertEquals(8, qiRepository.count());
		Assertions.assertEquals(0, qsRepository.count());
	}

	@Test
	void deleteStorage() {
		final var id = qsRepository.findByNameExpected("server1-root").getId();
		Assertions.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		em.flush();
		em.clear();

		checkCost(qsResource.delete(id), 7096.798, 9659.098, false);

		// Check the exact new cost
		checkCost(subscription, 7096.798, 9659.098, false);

		// Check the associations
		Assertions.assertNull(qsRepository.findOne(id));
		Assertions.assertEquals(2, qiRepository.findByNameExpected("server1").getStorages().size());
	}

	@Test
	void findType() {
		final var tableItem = qsResource.findType(subscription, newUriInfo());
		Assertions.assertEquals(6, tableItem.getRecordsTotal());
		Assertions.assertEquals("storage1", tableItem.getData().get(0).getName());
		Assertions.assertNull(tableItem.getData().get(0).getDatabaseType());
		Assertions.assertEquals("storage7-database", tableItem.getData().get(5).getName());
		Assertions.assertEquals("%", tableItem.getData().get(5).getDatabaseType());
	}

	@Test
	void findTypeCriteria() {
		final var tableItem = qsResource.findType(subscription, newUriInfo("rAge2"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("storage2", tableItem.getData().get(0).getName());
	}

	@Test
	void findTypeNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qsResource.findType(-1, newUriInfo()));
	}

	@Test
	void findTypeAnotherSubscription() {
		Assertions.assertEquals(1,
				qsResource.findType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.findType(subscription, newUriInfo()));
	}

	/**
	 * Builder coverage
	 */
	@Test
	void queryJson() throws IOException {
		new ObjectMapperTrim().readValue("{\"size\":1,\"latency\":\"GOOD\","
				+ "\"instance\":2,\"database\":2,\"optimized\":\"IOPS\"," + "\"location\":\"L\"}",
				QuoteStorageQuery.class);
		QuoteStorageQuery.builder().toString();
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookupStorage() {
		final var price = qsResource
				.lookup(subscription, QuoteStorageQuery.builder().size(2).optimized(ProvStorageOptimized.IOPS).build())
				.get(0);

		// Check the storage result
		assertCSP(price);
		Assertions.assertEquals(0.42, price.getCost(), DELTA);
		Assertions.assertEquals(2, price.getSize());
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	void lookupStorageHighConstraints() throws IOException {
		final var lookup = qsResource
				.lookup(subscription, QuoteStorageQuery.builder().size(1024).latency(Rate.GOOD).build()).get(0);
		final var asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":215.04,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":0.0,\"location\":\"region-1\",\"type\":{\"id\":"));
		Assertions.assertTrue(asJson.endsWith("\"name\":\"storage1\",\"description\":\"storageD1\",\"latency\":\"good\""
				+ ",\"optimized\":\"iops\",\"minimal\":1,\"maximal\":null,\"iops\":200,\"throughput\":60"
				+ ",\"instanceType\":\"%\",\"databaseType\":null,\"engine\":null"
				+ ",\"availability\":99.99,\"durability9\":11}"
				+ ",\"costGb\":0.21,\"costTransaction\":0.0},\"size\":1024}"));

		// Check the storage result
		assertCSP(lookup);
		Assertions.assertEquals(215.04, lookup.getCost(), DELTA);
	}

	private QuoteStorageLookup assertCSP(final QuoteStorageLookup price) {
		final var sp = price.getPrice();
		final var st = sp.getType();
		Assertions.assertNotNull(sp.getId());
		Assertions.assertNotNull(st.getId());
		Assertions.assertEquals("storage1", st.getName());
		return price;
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupStorageNoMatch() {
		Assertions.assertEquals("storage1",
				qsResource.lookup(subscription, QuoteStorageQuery.builder().size(512).latency(Rate.GOOD).build()).get(0)
						.getPrice().getType().getName());
		Assertions.assertEquals("storage1",
				qsResource.lookup(subscription, QuoteStorageQuery.builder().size(999).latency(Rate.GOOD).build()).get(0)
						.getPrice().getType().getName());
		Assertions.assertEquals("storage2",
				qsResource.lookup(subscription, QuoteStorageQuery.builder().size(512).latency(Rate.MEDIUM).build())
						.get(0).getPrice().getType().getName());

		// Out of limits
		Assertions.assertEquals("storage1",
				qsResource.lookup(subscription, QuoteStorageQuery.builder().size(999).latency(Rate.MEDIUM).build())
						.get(0).getPrice().getType().getName());
	}

	/**
	 * All quote instances are based on the default quote's location : "region1". But lookup on "region-2" and
	 * attachment to a "region-1" instance.
	 */
	@Test
	void lookupStorageLocationNoMatchInstance() {
		Assertions.assertEquals(0, qsResource
				.lookup(subscription, QuoteStorageQuery.builder().instance(server1()).location("region-2").build())
				.size());
	}

	/**
	 * All quote instances are based on the default quote's location : "region1". And this is the requested location.
	 */
	@Test
	void lookupStorageExactLocationInstance() {
		Assertions.assertEquals(3, qsResource
				.lookup(subscription, QuoteStorageQuery.builder().instance(server1()).location("region-1").build())
				.size());
	}

	/**
	 * All quote databases are based on the default quote's location : "region1". And this is the requested location.
	 */
	@Test
	void lookupStorageExactLocationDatabase() {
		Assertions.assertEquals(1, qsResource
				.lookup(subscription, QuoteStorageQuery.builder().database(database1()).location("region-1").build())
				.size());
	}

	/**
	 * All quote instances are based on the default quote's location : "region1" but no instance attachment is requested
	 */
	@Test
	void lookupStorageAnotherLocationNoInstance() {
		Assertions.assertEquals(3,
				qsResource.lookup(subscription, QuoteStorageQuery.builder().location("region-2").build()).size());
	}

	/**
	 * All quote instances are based on the default quote's location : "region1", and lookup for an storage whatever the
	 * location but attached to instance located in "region-1".
	 */
	@Test
	void lookupStorageAnotherLocationNoRegion() {
		Assertions.assertEquals(3,
				qsResource.lookup(subscription, QuoteStorageQuery.builder().instance(server1()).build()).size());
	}

	/**
	 * All quote instances are based on the default quote's location : "region1", lookup for a storage whatever the
	 * location, and without instance compatibility requirement.
	 */
	@Test
	void lookupStorageAnotherLocationNotInstance() {
		Assertions.assertEquals(6, qsResource.lookup(subscription, new QuoteStorageQuery()).size());
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
}