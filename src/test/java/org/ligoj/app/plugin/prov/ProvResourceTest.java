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
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;
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
	private ProvInstancePriceTypeRepository priceTypeRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvStorageTypeRepository storageRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	private int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvQuote.class, ProvStorageType.class,
				ProvInstancePriceType.class, ProvInstance.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		refreshCost();
	}

	@Test
	public void testBusiness() {
		// Coverage only
		Assert.assertEquals(InternetAccess.PUBLIC.ordinal(), InternetAccess.valueOf(InternetAccess.values()[0].name()).ordinal());
		Assert.assertNotNull(resource.getSubscriptionRepository());
		Assert.assertNotNull(resource.getTaskRepository());
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
		Assert.assertEquals(43576, status.getTotalRam());
		Assert.assertEquals(4, status.getNbStorages());
		Assert.assertEquals(94, status.getTotalStorage());
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
		final QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote1", vo.getName());
		Assert.assertEquals("quoteD1", vo.getDescription());
		checkCost(vo.getCost(), 4692.785, 7139.185, false);
		Assert.assertNotNull(vo.getId());
		Assert.assertNotNull(vo.getCreatedBy());
		Assert.assertNotNull(vo.getCreatedDate());
		Assert.assertNotNull(vo.getLastModifiedBy());
		Assert.assertNotNull(vo.getLastModifiedDate());

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
		final ProvInstancePrice instancePrice = quoteInstance.getInstancePrice();
		Assert.assertEquals(0.2, instancePrice.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, instancePrice.getOs());
		Assert.assertNotNull(instancePrice.getType().getId());
		Assert.assertEquals(15, instancePrice.getType().getPeriod().intValue());
		Assert.assertEquals(60, instancePrice.getType().getMinimum().intValue());
		Assert.assertEquals("on-demand1", instancePrice.getType().getName());
		Assert.assertEquals("15 minutes fragment", instancePrice.getType().getDescription());
		final ProvInstance instance = instancePrice.getInstance();
		Assert.assertNotNull(instance.getId().intValue());
		Assert.assertEquals("instance1", instance.getName());
		Assert.assertEquals("instanceD1", instance.getDescription());
		Assert.assertEquals(0.5, instance.getCpu(), 0.0001);
		Assert.assertEquals(2000, instance.getRam().intValue());
		Assert.assertTrue(instance.getConstant());

		// No minimal for this instance price
		Assert.assertNull(instances.get(1).getInstancePrice().getType().getMinimum());
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
		final List<QuoteStorageVo> storages = vo.getStorages();
		Assert.assertEquals(4, storages.size());
		final QuoteStorageVo quoteStorage = storages.get(0);
		Assert.assertNotNull(quoteStorage.getId());
		Assert.assertEquals("server1-root", quoteStorage.getName());
		Assert.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assert.assertEquals(20, quoteStorage.getSize());
		Assert.assertEquals(8.4, quoteStorage.getCost(), DELTA);
		Assert.assertNotNull(quoteStorage.getQuoteInstance());
		final ProvStorageType storage = quoteStorage.getType();
		Assert.assertNotNull(storage.getId());
		Assert.assertEquals(0.21, storage.getCostGb(), DELTA);
		Assert.assertEquals(0, storage.getCost(), DELTA);
		Assert.assertEquals("storage1", storage.getName());
		Assert.assertEquals("storageD1", storage.getDescription());
		Assert.assertEquals(0, storage.getCostTransaction(), DELTA);
		Assert.assertEquals(ProvStorageFrequency.HOT, storage.getFrequency());
		Assert.assertEquals(ProvStorageOptimized.IOPS, storage.getOptimized());

		// Not attached storage
		Assert.assertNull(storages.get(3).getQuoteInstance());

		// Check the small transactional cost
		Assert.assertEquals(0.000000072, storages.get(1).getType().getCostTransaction(), 0.000000001);

	}

	@Test
	public void getConfigurationEmpty() {
		checkEmpty();
		checkCost0(resource.refreshCost(checkEmpty()));
		checkEmpty();
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
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 0, 0, null, VmOs.LINUX,
				null, null);

		// Check the instance result
		final ProvInstancePrice pi = price.getInstance().getInstance();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance2", pi.getInstance().getName());
		Assert.assertEquals(1, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(2000, pi.getInstance().getRam().intValue());
		Assert.assertEquals(0.14, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("1y", pi.getType().getName());
		Assert.assertEquals(102.2, price.getInstance().getCost(), DELTA);

		// Check the custom instance price
		final ProvInstancePrice pic = price.getCustom().getInstance();
		Assert.assertNotNull(pic.getId());
		Assert.assertEquals("dynamic", pic.getInstance().getName());
		Assert.assertEquals(0, pic.getInstance().getCpu().intValue());
		Assert.assertEquals(0, pic.getInstance().getRam().intValue());
		Assert.assertEquals(0, pic.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("on-demand1", pic.getType().getName());
		Assert.assertEquals(102.2, price.getInstance().getCost(), DELTA);

	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupInstanceHighContraints() throws IOException {
		final LowestInstancePrice price = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim().writeValueAsString(resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 3, 9,
						true, VmOs.WINDOWS, null, priceTypeRepository.findByNameExpected("on-demand1").getId())),
				LowestInstancePrice.class);
		final ProvInstancePrice pi = price.getInstance().getInstance();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance9", pi.getInstance().getName());
		Assert.assertEquals(4, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(16000, pi.getInstance().getRam().intValue());
		Assert.assertTrue(pi.getInstance().getConstant());
		Assert.assertEquals(5.6, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assert.assertEquals("on-demand1", pi.getType().getName());
		Assert.assertFalse(pi.getInstance().isCustom());

		// Not serialized
		Assert.assertNull(pi.getInstance().getNode());
		Assert.assertNull(pi.getType().getNode());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceOnlyCustom() {
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999, 0, false,
				VmOs.LINUX, null, priceTypeRepository.findByNameExpected("1y").getId());
		Assert.assertNull(price.getInstance());
		Assert.assertNull(price.getCustom());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceNoMatch() {
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999, 0, null,
				VmOs.LINUX, null, null);
		Assert.assertNull(price.getInstance());

		// Check the custom instance
		final ProvInstancePrice pi = price.getCustom().getInstance();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("dynamic", pi.getInstance().getName());
		Assert.assertEquals(0, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(0, pi.getInstance().getRam().intValue());
		Assert.assertTrue(pi.getInstance().getConstant());
		Assert.assertEquals(0, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("on-demand1", pi.getType().getName());
		Assert.assertTrue(pi.getInstance().isCustom());

		Assert.assertEquals(241928.03, price.getCustom().getCost(), DELTA);
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
		vo.setType(storageRepository.findByNameExpected("storage3").getId());
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
		vo.setType(storageRepository.findByNameExpected("storage3").getId());
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
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
		Assert.assertEquals(2.15, storage.getCost(), DELTA);
		Assert.assertFalse(storage.isUnboundCost());
	}

	@Test
	public void createStorageInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-bis");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4907.825, 8214.385, false);
		checkCost(cost.getResourceCost(), 215.04, 1075.2, false);
		Assert.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4907.825, 8214.385, false);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
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
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(quoteInstance.getId());
		vo.setSize(512);
		final UpdatedCost cost = resource.createStorage(vo);
		checkCost(cost.getTotalCost(), 4907.825, 4907.825, true);
		checkCost(cost.getResourceCost(), 215.04, 215.04, true);
		Assert.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 4907.825, 4907.825, true);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
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
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
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
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
		Assert.assertEquals(53.76, storage.getCost(), DELTA);
		Assert.assertFalse(storage.isUnboundCost());
	}

	@Test(expected = EntityNotFoundException.class)
	public void createStorageNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
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
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
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
		Assert.assertEquals(0, cost.getRelatedCosts().size());

		checkCost(subscription, 4692.785, 4692.785, true);
		Assert.assertTrue(qsRepository.findOneExpected(vo.getId()).isUnboundCost());
	}

	@Test
	public void updateStorageNoLimit() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		resource.updateStorage(vo);

		// Check the exact new cost
		checkCost(subscription, 4899.425, 8172.385, false);
		Assert.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
	}

	@Test
	public void updateStorageLimit() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setDescription("server1-root-bisD");

		// Change the storage type -> storage2 has a minimal to 512
		vo.setType(storageRepository.findByNameExpected("storage2").getId());
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
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
		Assert.assertEquals(77.8, storage.getCost(), DELTA);
	}

	@Test(expected = ValidationJsonException.class)
	public void updateStorageLimitKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType(storageRepository.findByNameExpected("storage2").getId());
		vo.setSize(1024); // Limit for this storage is 512
		resource.updateStorage(vo);
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void updateStorageUnknownStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType(-1);
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
		vo.setType(storageRepository.findByNameExpected("storageX").getId());
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void updateStorageUnknownInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
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
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("serverX").getId());
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateInstanceNonVisibleInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setInstancePrice(ipRepository.findBy("instance.name", "instanceX").getId());
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
		Assert.assertTrue(qsRepository.exists(storage1));
		Assert.assertTrue(qsRepository.exists(storage2));
		Assert.assertTrue(qsRepository.exists(storage3));
		Assert.assertEquals(8, qiRepository.count());
		em.flush();
		em.clear();

		checkCost(resource.deleteAllInstances(subscription), 2.73, 2.73, false);

		// Check the exact new cost
		checkCost(subscription, 2.73, 2.73, false);
		Assert.assertNull(qiRepository.findOne(id));
		Assert.assertEquals(1, qiRepository.count());

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.exists(storage1));
		Assert.assertFalse(qsRepository.exists(storage2));
		Assert.assertFalse(qsRepository.exists(storage3));
		Assert.assertTrue(qsRepository.exists(storageOther));
	}

	@Test
	public void deleteInstance() {
		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assert.assertTrue(qsRepository.exists(storage1));
		Assert.assertTrue(qsRepository.exists(storage2));
		Assert.assertTrue(qsRepository.exists(storage3));
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		em.flush();
		em.clear();

		checkCost(resource.deleteInstance(id), 4081.185, 4081.185, false);

		// Check the exact new cost
		checkCost(subscription, 4081.185, 4081.185, false);
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assert.assertNull(qiRepository.findOne(id));

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.exists(storage1));
		Assert.assertFalse(qsRepository.exists(storage2));
		Assert.assertFalse(qsRepository.exists(storage3));
		Assert.assertTrue(qsRepository.exists(storageOther));
	}

	@Test
	public void deleteUnboundInstance() {
		Assert.assertEquals(0, repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
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
	public void updateInstanceIdentity() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assert.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.2).getId());
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
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.2).getId());
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

	@Test
	public void updateInstance() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assert.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
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
	}

	@Test
	public void createInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setConstant(true);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setMaxVariableCost(210.9);
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
		Assert.assertEquals("serverZD", instance.getDescription());
		Assert.assertEquals(1024, instance.getRam().intValue());
		Assert.assertEquals(0.5, instance.getCpu(), DELTA);
		Assert.assertEquals(2080.5, instance.getCost(), DELTA);
		Assert.assertEquals(3120.75, instance.getMaxCost(), DELTA);
		Assert.assertTrue(instance.getConstant());
		Assert.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assert.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assert.assertEquals(10, instance.getMinQuantity().intValue());
		Assert.assertEquals(15, instance.getMaxQuantity().intValue());
		Assert.assertFalse(instance.isUnboundCost());
	}

	@Test
	public void createUnboundInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
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
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
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
	public void link() {
		final Subscription subscription = new Subscription();
		subscription.setNode(em.find(Subscription.class, this.subscription).getNode());
		subscription.setProject(em.find(Subscription.class, this.subscription).getProject());
		em.persist(subscription);
		em.flush();
		em.clear();
		resource.link(subscription.getId());
		final QuoteVo configuration = resource.getConfiguration(subscription.getId());
		Assert.assertNotNull(configuration);
		Assert.assertNotNull(configuration.getName());
		Assert.assertNotNull(configuration.getDescription());
	}

	@Test
	public void findInstancePriceType() {
		final TableItem<ProvInstancePriceType> tableItem = resource.findInstancePriceType(subscription, newUriInfo());
		Assert.assertEquals(3, tableItem.getRecordsTotal());
		Assert.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTypeCriteria() {
		final TableItem<ProvInstancePriceType> tableItem = resource.findInstancePriceType(subscription, newUriInfo("deMand"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
		Assert.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void findInstancePriceTypeNotExistsSubscription() {
		resource.findInstancePriceType(-1, newUriInfo());
	}

	@Test
	public void findInstancePriceTypeAnotherSubscription() {
		Assert.assertEquals(1, resource.findInstancePriceType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test(expected = EntityNotFoundException.class)
	public void findInstancePriceTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		resource.findInstancePriceType(subscription, newUriInfo());
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
		final TableItem<ProvInstance> tableItem = resource.findInstance(subscription, newUriInfo());
		Assert.assertEquals(13, tableItem.getRecordsTotal());
		Assert.assertEquals("instance1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstanceCriteria() {
		final TableItem<ProvInstance> tableItem = resource.findInstance(subscription, newUriInfo("sTance1"));
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
		final ComputedStoragePrice price = resource.lookupStorage(subscription, 2, null, null, ProvStorageOptimized.IOPS).get(0);

		// Check the storage result
		final ProvStorageType st = price.getType();
		Assert.assertNotNull(st.getId());
		Assert.assertEquals("storage1", st.getName());
		Assert.assertEquals(0.42, price.getCost(), DELTA);
		Assert.assertEquals(2, price.getSize());
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupStorageHighContraints() throws IOException {
		final ComputedStoragePrice price = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim()
						.writeValueAsString(resource.lookupStorage(subscription, 1024, ProvStorageFrequency.HOT, null, null).get(0)),
				ComputedStoragePrice.class);

		// Check the storage result
		final ProvStorageType st = price.getType();
		Assert.assertNotNull(st.getId());
		Assert.assertEquals("storage1", st.getName());
		Assert.assertEquals(215.04, price.getCost(), DELTA);

		// Not serialized
		Assert.assertNull(st.getNode());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupStorageNoMatch() {
		Assert.assertFalse(resource.lookupStorage(subscription, 512, ProvStorageFrequency.HOT, null, null).isEmpty());
		Assert.assertFalse(resource.lookupStorage(subscription, 999, ProvStorageFrequency.HOT, null, null).isEmpty());
		Assert.assertFalse(resource.lookupStorage(subscription, 512, ProvStorageFrequency.COLD, null, null).isEmpty());

		// Out of limits
		Assert.assertTrue(resource.lookupStorage(subscription, 999, ProvStorageFrequency.COLD, null, null).isEmpty());
	}

	@Test
	public void upload() throws IOException {
		resource.upload(subscription, new ClassPathResource("csv/upload.csv").getInputStream(),
				new String[] { "name", "cpu", "ram", "disk", "frequency", "os", "constant" }, null, 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(18, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(17).getInstancePrice().getType().getName());
		Assert.assertEquals(15, configuration.getStorages().size());
		Assert.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14917.079, 17363.479, false);
	}

	@Test
	public void uploadDefaultHeader() throws IOException {
		resource.upload(subscription, new ClassPathResource("csv/upload-default.csv").getInputStream(), null, null, 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(18, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(17).getInstancePrice().getType().getName());
		Assert.assertEquals(1, configuration.getInstances().get(17).getMinQuantity().intValue());
		Assert.assertEquals(1, configuration.getInstances().get(17).getMaxQuantity().intValue());
		Assert.assertNull(configuration.getInstances().get(17).getMaxVariableCost());
		Assert.assertEquals("dynamic", configuration.getInstances().get(12).getInstancePrice().getInstance().getName());
		Assert.assertEquals(14, configuration.getStorages().size());
		Assert.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14839.279, 17285.679, false);
	}

	@Test
	public void uploadDefaultPriceType() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "constant" }, priceTypeRepository.findByNameExpected("on-demand2").getId(), 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand2", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance1", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.535, 7266.935, false);
	}

	@Test
	public void uploadFixedInstance() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "instance" }, priceTypeRepository.findByNameExpected("on-demand2").getId(), 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand2", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance10", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadBoundQuantities() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1000".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assert.assertEquals(1, qi.getMinQuantity().intValue());
		Assert.assertEquals(1000, qi.getMaxQuantity().intValue());
		Assert.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 135099.185, false);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assert.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 210, false);
	}

	@Test
	public void uploadMaxQuantities() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
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
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;0".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
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
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;PUBLIC".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "instance", "internet" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("instance10", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadFixedPriceType() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;on-demand1;66".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "priceType", "maxVariableCost" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance2", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(66, configuration.getInstances().get(7).getMaxVariableCost(), DELTA);
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4827.835, 7274.235, false);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;LINUX".getBytes("UTF-8")), null, null, 1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 246640.288, 249086.688, false);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;1;64;LINUX".getBytes("UTF-8")), null, null, 1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 5142.672, 7589.072, false);
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadInstanceNotFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;WINDOWS".getBytes("UTF-8")), null,
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1024, "UTF-8");
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadStorageNotFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;1;1;LINUX;99999999999;HOT;THROUGHPUT".getBytes("UTF-8")), null,
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1, "UTF-8");
	}

	@Test(expected = BusinessException.class)
	public void uploadInvalidHeader() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY".getBytes("UTF-8")), new String[] { "any" },
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1, "UTF-8");
	}

	@Test
	public void update() {
		final DescribedBean<?> nameDesc = new DescribedBean<>();
		nameDesc.setName("name1");
		nameDesc.setDescription("description1");
		resource.update(subscription, nameDesc);
		Assert.assertEquals("description1", repository.findByNameExpected("name1").getDescription());
	}
}
