package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
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
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
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
public class ProvQuoteStorageResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteStorageResource sResource;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

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

	private QuoteLigthVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
	}

	/**
	 * Attempt to attach a storage incompatible to an instance.
	 */
	@Test
	public void createStorageInstanceKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(1);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			sResource.create(vo);
		}), "type", "type-incompatible-requirements");
	}

	/**
	 * Attempt to attach a storage compatible to an instance but without an instance.
	 */
	@Test
	public void createStorageNoInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("storage3-root-bis");
		vo.setType("storage3");
		vo.setSize(1);
		final UpdatedCost cost = sResource.create(vo);
		checkCost(cost.getTotalCost(), 4706.908, 7156.508, false);
		checkCost(cost.getResourceCost(), 2.15, 2.15, false);
		Assertions.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4706.908, 7156.508, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("storage3-root-bis", storage.getName());
		Assertions.assertEquals(1, storage.getSize().intValue());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(2.15, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	@Test
	public void createStorageInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		final UpdatedCost cost = sResource.create(vo);
		checkCost(cost.getTotalCost(), 4919.798, 8229.558, false);
		checkCost(cost.getResourceCost(), 215.04, 1075.2, false);
		Assertions.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292.8, 1464.0, false);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4919.798, 8229.558, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals(512, storage.getSize().intValue());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(215.04, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
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
		final UpdatedCost cost = sResource.create(vo);
		checkCost(cost.getTotalCost(), 4919.798, 4919.798, true);
		checkCost(cost.getResourceCost(), 215.04, 215.04, true);
		Assertions.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292.8, 292.8, true);
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4919.798, 4919.798, true);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals(512, storage.getSize().intValue());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(215.04, storage.getCost(), DELTA);
		Assertions.assertTrue(storage.isUnboundCost());
	}

	/**
	 * Change the given instance name from bound to unbound type.
	 */
	private ProvQuoteInstance setUnboundInstance(final String name) {
		final ProvQuoteInstance quoteInstance = qiRepository.findByNameExpected(name);

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
	public void createStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setDescription("server1-root-terD");
		vo.setType("storage1");
		vo.setSize(256);
		final UpdatedCost cost = sResource.create(vo);
		checkCost(cost.getTotalCost(), 4758.518, 7208.118, false);
		checkCost(cost.getResourceCost(), 53.76, 53.76, false);
		Assertions.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4758.518, 7208.118, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assertions.assertEquals("server1-root-ter", storage.getName());
		Assertions.assertEquals("server1-root-terD", storage.getDescription());
		Assertions.assertEquals(256, storage.getSize().intValue());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(53.76, storage.getCost(), DELTA);
		Assertions.assertFalse(storage.isUnboundCost());
	}

	@Test
	public void refresh() {
		// Create with constraints
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server-new");
		vo.setType("storage1");
		vo.setOptimized(null);
		vo.setInstanceCompatible(true);
		vo.setLatency(Rate.GOOD);
		vo.setQuoteInstance(qiRepository.findByNameExpected("server2").getId());
		vo.setSize(512);
		final UpdatedCost cost = sResource.create(vo);
		checkCost(cost.getResourceCost(), 107.52, 107.52, false);

		// No change
		checkCost(sResource.refresh(qsRepository.findOneExpected(cost.getId())), 107.52, 107.52, false);
		Assertions.assertEquals("storage1", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());
		Assertions.assertEquals(true, qsRepository.findOneExpected(cost.getId()).getInstanceCompatible());

		// Change some constraints
		vo.setLatency(Rate.WORST);
		vo.setInstanceCompatible(false);
		vo.setQuoteInstance(null);
		vo.setId(cost.getId());

		// Cost is the same since the type still match the constraints
		checkCost(sResource.update(vo).getResourceCost(), 107.52, 107.52, false);

		// The cost changed since a best type matches to the constraints
		checkCost(sResource.refresh(qsRepository.findOneExpected(cost.getId())), 77.8, 77.8, false);
		Assertions.assertEquals("storage2", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change the price of "storage3" to make it as cheap as "storage2"
		final ProvStoragePrice price3 = spRepository.findBy("type.name", "storage3");
		price3.setCost(1);
		spRepository.saveAndFlush(price3);

		// Also, change the latency of "storage3" to "GOOD" class
		final ProvStorageType type3 = stRepository.findByName("storage3");
		type3.setLatency(Rate.GOOD);
		stRepository.saveAndFlush(type3);

		// Even if "storage2" and "storage3" have identical prices and match to
		// the
		// requirements, "storage3" offers a lowest latency.
		checkCost(sResource.refresh(qsRepository.findOneExpected(cost.getId())), 77.8, 77.8, false);
		Assertions.assertEquals("storage3", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());
	}

	@Test
	public void createStorageNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setType("storage1");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.create(vo);
		});
	}

	@Test
	public void updateStorageDetachAttach() {
		checkCost(subscription, 4704.758, 7154.358, false);

		// Make "server1" with unbound maximal quantities
		setUnboundInstance("server1");

		// Check the new cost corresponds to the minimal cost since there is
		// only
		// one unbound instance
		checkCost(subscription, 4704.758, 4704.758, true);

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
		UpdatedCost cost = sResource.update(vo);
		checkCost(cost.getTotalCost(), 4700.558, 4700.558, true);
		checkCost(cost.getResourceCost(), 4.2, 4.2, false);
		Assertions.assertEquals(0, cost.getRelatedCosts().size());

		// Check the exact new cost
		checkCost(subscription, 4700.558, 4700.558, true);
		final ProvQuoteStorage qs = qsRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-root-bis", qs.getName());
		Assertions.assertFalse(qs.isUnboundCost());

		// Attach back this storage to "server1
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		cost = sResource.update(vo);
		checkCost(cost.getTotalCost(), 4704.758, 4704.758, true);
		checkCost(cost.getResourceCost(), 8.4, 8.4, true);
		Assertions.assertEquals(1, cost.getRelatedCosts().size());
		checkCost(cost.getRelatedCosts().get(vo.getQuoteInstance()), 292.8, 292.8, true);

		checkCost(subscription, 4704.758, 4704.758, true);
		Assertions.assertTrue(qsRepository.findOneExpected(vo.getId()).isUnboundCost());
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
		sResource.update(vo);

		// Check the exact new cost
		checkCost(subscription, 4911.398, 8187.558, false);
		Assertions.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
	}

	@Test
	public void updateStorageInvalidLocation() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		vo.setLocation("region-Z");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.update(vo);
		});
	}

	@Test
	public void updateStorageNotVisibleLocation() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		vo.setLocation("region-3");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.update(vo);
		});
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
		sResource.update(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4774.158, 7190.158, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-root-bis", storage.getName());
		Assertions.assertEquals("server1-root-bisD", storage.getDescription());
		Assertions.assertEquals(512, storage.getSize().intValue());
		Assertions.assertEquals(vo.getType(), storage.getPrice().getType().getName());
		Assertions.assertEquals(77.8, storage.getCost(), DELTA);
	}

	@Test
	public void updateStorageLimitKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage2");
		vo.setSize(1024); // Limit for this storage is 512
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			sResource.update(vo);
		}), "type", "type-incompatible-requirements");
	}

	@Test
	public void updateStorageUnknownStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage-unknown");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.update(vo);
		});
	}

	/**
	 * Selected storage is not within the same provider.
	 */
	@Test
	public void updateStorageInvalidStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storageX");
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.update(vo);
		});
	}

	@Test
	public void updateStorageUnknownInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(-1);
		vo.setSize(1);
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> {
			sResource.update(vo);
		});
	}

	/**
	 * Selected instance is not within the same provider.
	 */
	@Test
	public void updateStorageNonVisibleInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType("storage1");
		vo.setQuoteInstance(qiRepository.findByNameExpected("serverX").getId());
		vo.setSize(1);
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.update(vo);
		});
	}

	@Test
	public void deleteAllStorages() {
		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assertions.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		Assertions.assertEquals(8, qiRepository.count());
		Assertions.assertEquals(4, qsRepository.count());
		em.flush();
		em.clear();

		checkCost(sResource.deleteAll(subscription), 4382.428, 5553.628, false);

		// Check the exact new cost
		checkCost(subscription, 4382.428, 5553.628, false);

		// Check the associations
		Assertions.assertNull(qsRepository.findOne(id));
		Assertions.assertEquals(0, qiRepository.findByNameExpected("server1").getStorages().size());

		Assertions.assertEquals(8, qiRepository.count());
		Assertions.assertEquals(0, qsRepository.count());
	}

	@Test
	public void deleteStorage() {
		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assertions.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		em.flush();
		em.clear();

		checkCost(sResource.delete(id), 4696.358, 7112.358, false);

		// Check the exact new cost
		checkCost(subscription, 4696.358, 7112.358, false);

		// Check the associations
		Assertions.assertNull(qsRepository.findOne(id));
		Assertions.assertEquals(2, qiRepository.findByNameExpected("server1").getStorages().size());
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
	public void findStorageType() {
		final TableItem<ProvStorageType> tableItem = sResource.findType(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("storage1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findStorageTypeCriteria() {
		final TableItem<ProvStorageType> tableItem = sResource.findType(subscription, newUriInfo("rAge2"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("storage2", tableItem.getData().get(0).getName());
	}

	@Test
	public void findStorageTypeNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> {
			sResource.findType(-1, newUriInfo());
		});
	}

	@Test
	public void findStorageTypeAnotherSubscription() {
		Assertions.assertEquals(1,
				sResource.findType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findStorageTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			sResource.findType(subscription, newUriInfo());
		});
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupStorage() {
		final QuoteStorageLoopup price = sResource.lookup(subscription, 2, null, null, ProvStorageOptimized.IOPS, null)
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
	public void lookupStorageHighContraints() throws IOException {
		final QuoteStorageLoopup lookup = sResource.lookup(subscription, 1024, Rate.GOOD, null, null, null).get(0);
		final String asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":215.04,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":0.0,\"location\":\"region-1\",\"type\":{\"id\":"));
		Assertions.assertTrue(asJson.endsWith("\"name\":\"storage1\",\"description\":\"storageD1\",\"latency\":\"good\""
				+ ",\"optimized\":\"iops\",\"minimal\":1,\"maximal\":null,\"iops\":200,\"throughput\":60"
				+ ",\"instanceCompatible\":true},\"costGb\":0.21,\"costTransaction\":0.0},\"size\":1024}"));

		// Check the storage result
		assertCSP(lookup);
		Assertions.assertEquals(215.04, lookup.getCost(), DELTA);
	}

	private QuoteStorageLoopup assertCSP(final QuoteStorageLoopup price) {
		final ProvStoragePrice sp = price.getPrice();
		final ProvStorageType st = sp.getType();
		Assertions.assertNotNull(sp.getId());
		Assertions.assertNotNull(st.getId());
		Assertions.assertEquals("storage1", st.getName());
		return price;
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupStorageNoMatch() {
		Assertions.assertEquals("storage1",
				sResource.lookup(subscription, 512, Rate.GOOD, null, null, null).get(0).getPrice().getType().getName());
		Assertions.assertEquals("storage1",
				sResource.lookup(subscription, 999, Rate.GOOD, null, null, null).get(0).getPrice().getType().getName());
		Assertions.assertEquals("storage2", sResource.lookup(subscription, 512, Rate.MEDIUM, null, null, null).get(0)
				.getPrice().getType().getName());

		// Out of limits
		Assertions.assertEquals("storage1", sResource.lookup(subscription, 999, Rate.MEDIUM, null, null, null).get(0)
				.getPrice().getType().getName());
	}
}
