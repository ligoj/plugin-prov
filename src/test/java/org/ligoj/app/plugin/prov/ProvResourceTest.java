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
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorage;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.plugin.prov.model.VmStorageType;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
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
	private ProvInstancePriceTypeRepository priceTypeRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvStorageRepository storageRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	private int subscription;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvQuote.class, ProvStorage.class,
						ProvInstancePriceType.class, ProvInstance.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
	}

	@Test
	public void getSusbcriptionStatus() {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		Assert.assertEquals("quote1", status.getName());
		Assert.assertEquals("quoteD1", status.getDescription());
		Assert.assertNotNull(status.getId());
		Assert.assertEquals(0.128, status.getCost(), 0.0001);
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
		Assert.assertEquals(0, status.getCost(), 0.0001);
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
		Assert.assertEquals(0.128, vo.getCost(), 0.00001);
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

		// Check storage
		final List<QuoteStorageVo> storages = vo.getStorages();
		Assert.assertEquals(4, storages.size());
		final QuoteStorageVo quoteStorage = storages.get(0);
		Assert.assertNotNull(quoteStorage.getId());
		Assert.assertEquals("server1-root", quoteStorage.getName());
		Assert.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assert.assertEquals(20, quoteStorage.getSize());
		Assert.assertNotNull(quoteStorage.getQuoteInstance());
		final ProvStorage storage = quoteStorage.getStorage();
		Assert.assertNotNull(storage.getId());
		Assert.assertEquals(0.21, storage.getCost(), DELTA);
		Assert.assertEquals("storage1", storage.getName());
		Assert.assertEquals("storageD1", storage.getDescription());
		Assert.assertEquals(0, storage.getTransactionalCost(), DELTA);
		Assert.assertEquals(VmStorageType.HOT, storage.getType());

		// Not attached storage
		Assert.assertNull(storages.get(3).getQuoteInstance());

		// Check the small transactional cost
		Assert.assertEquals(0.000000072, storages.get(1).getStorage().getTransactionalCost(), 0.000000001);

	}

	@Test
	public void getConfigurationEmpty() {
		final QuoteVo vo = resource.getConfiguration(getSubscription("mda", ProvResource.SERVICE_KEY));
		Assert.assertEquals("quote2", vo.getName());
		Assert.assertEquals("quoteD2", vo.getDescription());
		Assert.assertNotNull(vo.getId());
		Assert.assertEquals(0, vo.getCost(), 0.00001);

		// Check compute
		Assert.assertEquals(0, vo.getInstances().size());

		// Check storage
		Assert.assertEquals(0, vo.getStorages().size());
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void findInstance() {
		final LowestPrice price = resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 0, 0, false,
				VmOs.LINUX, null);

		// Check the instance result
		final ProvInstancePrice pi = price.getInstance().getInstance();
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance2", pi.getInstance().getName());
		Assert.assertEquals(1, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(2000, pi.getInstance().getRam().intValue());
		Assert.assertEquals(0.14, pi.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("1y", pi.getType().getName());
		Assert.assertEquals(102.48, price.getInstance().getCost(), DELTA);

		// Check the custom instance price
		final ProvInstancePrice pic = price.getCustom().getInstance();
		Assert.assertNotNull(pic.getId());
		Assert.assertEquals("dynamic", pic.getInstance().getName());
		Assert.assertEquals(0, pic.getInstance().getCpu().intValue());
		Assert.assertEquals(0, pic.getInstance().getRam().intValue());
		Assert.assertEquals(0, pic.getCost(), DELTA);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("on-demand1", pic.getType().getName());
		Assert.assertEquals(102.48, price.getInstance().getCost(), DELTA);

	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void findInstanceHighContraints() throws IOException {
		final LowestPrice price = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim()
						.writeValueAsString(resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 3,
								9, true, VmOs.WINDOWS, priceTypeRepository.findByNameExpected("on-demand1").getId())),
				LowestPrice.class);
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
	public void findInstanceOnlyCustom() {
		final LowestPrice price = resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999, 0, false,
				VmOs.LINUX, priceTypeRepository.findByNameExpected("1y").getId());
		Assert.assertNull(price.getInstance());
		Assert.assertNull(price.getCustom());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void findInstanceNoMatch() {
		final LowestPrice price = resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999, 0, false,
				VmOs.LINUX, null);
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

		Assert.assertEquals(24259.212, price.getCustom().getCost(), DELTA);
	}

	@Test
	public void getKey() {
		Assert.assertEquals("service:prov", resource.getKey());

		// Only there for coverage of associations required by JPA
		new ProvQuote().setStorages(null);
		new ProvQuote().getStorages();
		new ProvQuote().setInstances(null);
		new ProvQuoteInstance().setStorages(null);
		VmStorageType.valueOf(VmStorageType.HOT.name());
		VmOs.valueOf(VmOs.LINUX.name());
	}

	@Test
	public void updateStorageNoLimit() {
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setStorage(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		resource.updateStorage(vo);
		
		// Check the exact new cost
		Assert.assertEquals(3844.062, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		Assert.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
	}

	@Test(expected = ValidationJsonException.class)
	public void updateStorageLimitKo() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setStorage(storageRepository.findByNameExpected("storage2").getId());
		vo.setSize(1024); // Limit for this storage is 512
		resource.updateStorage(vo);
	}

	@Test
	public void updateStorageLimit() {
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setDescription("server1-root-bisD");
		
		// Change the storage type -> storage2 has a minimal to 512
		vo.setStorage(storageRepository.findByNameExpected("storage2").getId());
		vo.setSize(512); // Limit for this storage is 512
		resource.updateStorage(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		Assert.assertEquals(3813.342, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals("server1-root-bisD", storage.getDescription());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getStorage(), storage.getStorage().getId().intValue());
		Assert.assertEquals(76.8, storage.getCost(), DELTA);
	}

	@Test
	public void createStorage() {
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setDescription("server1-root-terD");
		vo.setStorage(storageRepository.findByNameExpected("storage1").getId());
		vo.setSize(256);
		final int id = resource.createStorage(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		Assert.assertEquals(3794.502, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-ter", storage.getName());
		Assert.assertEquals("server1-root-terD", storage.getDescription());
		Assert.assertEquals(256, storage.getSize().intValue());
		Assert.assertEquals(vo.getStorage(), storage.getStorage().getId().intValue());
		Assert.assertEquals(53.76, storage.getCost(), DELTA);
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void updateStorageUnknownStorage() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setStorage(-1);
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
		vo.setStorage(storageRepository.findByNameExpected("storageX").getId());
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void updateStorageUnknownInstance() {
		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setStorage(storageRepository.findByNameExpected("storage1").getId());
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
		vo.setStorage(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("serverX").getId());
		vo.setSize(1);
		resource.updateStorage(vo);
	}

	@Test(expected = EntityNotFoundException.class)
	public void updateInstanceNonVisibleInstance() {
		refreshCost();

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
	public void deleteInstance() {
		refreshCost();

		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assert.assertTrue(qsRepository.exists(storage1));
		Assert.assertTrue(qsRepository.exists(storage2));
		Assert.assertTrue(qsRepository.exists(storage3));
		em.flush();
		em.clear();

		resource.deleteInstance(id);

		// Check the exact new cost
		Assert.assertEquals(3436.542, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		Assert.assertNull(qiRepository.findOne(id));

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.exists(storage1));
		Assert.assertFalse(qsRepository.exists(storage2));
		Assert.assertFalse(qsRepository.exists(storage3));
		Assert.assertTrue(qsRepository.exists(storageOther));
	}

	@Test
	public void deleteStorage() {
		refreshCost();

		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assert.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		em.flush();
		em.clear();

		resource.deleteStorage(id);

		// Check the exact new cost
		Assert.assertEquals(3736.542, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);

		// Check the associations
		Assert.assertNull(qsRepository.findOne(id));
		Assert.assertEquals(2, qiRepository.findByNameExpected("server1").getStorages().size());
	}

	private void refreshCost() {
		// Check the cost before updated cost (dummy value)
		Assert.assertEquals(0.128, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		
		// Check the cost fully updated and exact actual cost
		Assert.assertEquals(3740.742, resource.refreshCost(subscription), DELTA);
		Assert.assertEquals(3740.742, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		em.flush();
		em.clear();
	}

	@Test
	public void updateInstance() {
		refreshCost();

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		resource.updateInstance(vo);

		// Check the exact new cost
		Assert.assertEquals(3802.962, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		Assert.assertEquals("server1-bis", qiRepository.findOneExpected(vo.getId()).getName());
		Assert.assertEquals(1024, qiRepository.findOneExpected(vo.getId()).getRam().intValue());
		Assert.assertEquals(0.5, qiRepository.findOneExpected(vo.getId()).getCpu(), DELTA);
	}

	@Test
	public void checkSubscriptionStatus() throws Exception {
		final QuoteLigthVo quote = (QuoteLigthVo) new ProvServicePlugin() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		}.checkSubscriptionStatus(subscription, null, Collections.emptyMap()).getData().get("quote");
		Assert.assertNotNull(quote);
		Assert.assertEquals(0.128, quote.getCost(), 0.0001);
	}

	@Test
	public void create() {
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

	@Test
	public void findInstancePriceType() {
		final TableItem<ProvInstancePriceType> tableItem = resource.findInstancePriceType(subscription, newUriInfo());
		Assert.assertEquals(3, tableItem.getRecordsTotal());
		Assert.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test(expected = JpaObjectRetrievalFailureException.class)
	public void findInstancePriceTypeNotExistsSubscription() {
		resource.findInstancePriceType(-1, newUriInfo());
	}

	@Test
	public void findInstancePriceTypeAnotherSubscription() {
		Assert.assertEquals(1, resource.findInstancePriceType(getSubscription("mda", "service:prov:x"), newUriInfo())
				.getData().size());
	}

	@Test(expected = EntityNotFoundException.class)
	public void findInstancePriceTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		resource.findInstancePriceType(subscription, newUriInfo());
	}

}
