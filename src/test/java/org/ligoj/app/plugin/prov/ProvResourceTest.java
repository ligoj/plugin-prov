package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.cxf.jaxrs.impl.MetadataMap;
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
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
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
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvQuote.class, ProvStorageType.class,
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
		refreshCost();

		final QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote1", vo.getName());
		Assert.assertEquals("quoteD1", vo.getDescription());
		Assert.assertEquals(4396.614, vo.getCost(), 0.00001);
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
		Assert.assertEquals(4.2, quoteStorage.getCost(), DELTA);
		Assert.assertNotNull(quoteStorage.getQuoteInstance());
		final ProvStorageType storage = quoteStorage.getType();
		Assert.assertNotNull(storage.getId());
		Assert.assertEquals(0.21, storage.getCost(), DELTA);
		Assert.assertEquals("storage1", storage.getName());
		Assert.assertEquals("storageD1", storage.getDescription());
		Assert.assertEquals(0, storage.getTransactionalCost(), DELTA);
		Assert.assertEquals(ProvStorageFrequency.HOT, storage.getFrequency());
		Assert.assertEquals(ProvStorageOptimized.IOPS, storage.getOptimized());

		// Not attached storage
		Assert.assertNull(storages.get(3).getQuoteInstance());

		// Check the small transactional cost
		Assert.assertEquals(0.000000072, storages.get(1).getType().getTransactionalCost(), 0.000000001);

	}

	@Test
	public void getConfigurationEmpty() {
		checkEmpty();
		Assert.assertEquals(0, resource.refreshCost(checkEmpty()), DELTA);
		checkEmpty();
	}

	private int checkEmpty() {
		final int subscription = getSubscription("mda", ProvResource.SERVICE_KEY);
		final QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote2", vo.getName());
		Assert.assertEquals("quoteD2", vo.getDescription());
		Assert.assertNotNull(vo.getId());
		Assert.assertEquals(0, vo.getCost(), 0.00001);

		// Check compute
		Assert.assertEquals(0, vo.getInstances().size());

		// Check storage
		Assert.assertEquals(0, vo.getStorages().size());
		return subscription;
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupInstance() {
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 0,
				0, false, VmOs.LINUX, null, null);

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
	public void lookupInstanceHighContraints() throws IOException {
		final LowestInstancePrice price = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim().writeValueAsString(
						resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 3, 9, true,
								VmOs.WINDOWS, null, priceTypeRepository.findByNameExpected("on-demand1").getId())),
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
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999,
				0, false, VmOs.LINUX, null, priceTypeRepository.findByNameExpected("1y").getId());
		Assert.assertNull(price.getInstance());
		Assert.assertNull(price.getCustom());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceNoMatch() {
		final LowestInstancePrice price = resource.lookupInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999,
				0, false, VmOs.LINUX, null, null);
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

		Assert.assertEquals(242590.656, price.getCustom().getCost(), DELTA);
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
	}

	@Test
	public void updateStorageNoLimit() {
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qsRepository.findByNameExpected("server1-root").getId());
		vo.setName("server1-root-bis");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setQuoteInstance(qiRepository.findByNameExpected("server1").getId());
		vo.setSize(512);
		resource.updateStorage(vo);

		// Check the exact new cost
		Assert.assertEquals(4499.934, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		Assert.assertEquals("server1-root-bis", qsRepository.findOneExpected(vo.getId()).getName());
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

	@Test
	public void updateStorageLimit() {
		refreshCost();

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
		Assert.assertEquals(4469.214, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-root-bis", storage.getName());
		Assert.assertEquals("server1-root-bisD", storage.getDescription());
		Assert.assertEquals(512, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
		Assert.assertEquals(76.8, storage.getCost(), DELTA);
	}

	@Test
	public void createStorage() {
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setDescription("server1-root-terD");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setSize(256);
		final int id = resource.createStorage(vo);
		em.flush();
		em.clear();

		// Check the exact new cost
		Assert.assertEquals(4450.374, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteStorage storage = qsRepository.findOneExpected(id);
		Assert.assertEquals("server1-root-ter", storage.getName());
		Assert.assertEquals("server1-root-terD", storage.getDescription());
		Assert.assertEquals(256, storage.getSize().intValue());
		Assert.assertEquals(vo.getType(), storage.getType().getId().intValue());
		Assert.assertEquals(53.76, storage.getCost(), DELTA);
	}

	@Test(expected = EntityNotFoundException.class)
	public void createStorageNotVisibleSubscription() {
		initSpringSecurityContext("any");
		refreshCost();

		final QuoteStorageEditionVo vo = new QuoteStorageEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server1-root-ter");
		vo.setType(storageRepository.findByNameExpected("storage1").getId());
		vo.setSize(1);
		resource.createStorage(vo);
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
	public void delete() {
		refreshCost();
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
		refreshCost();

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

		resource.deleteAllInstances(subscription);

		// Check the exact new cost
		Assert.assertEquals(2.73, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
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
		Assert.assertEquals(4092.414, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		Assert.assertNull(qiRepository.findOne(id));

		// Also check the associated storage is deleted
		Assert.assertFalse(qsRepository.exists(storage1));
		Assert.assertFalse(qsRepository.exists(storage2));
		Assert.assertFalse(qsRepository.exists(storage3));
		Assert.assertTrue(qsRepository.exists(storageOther));
	}

	@Test
	public void deleteAllStorages() {
		refreshCost();

		final Integer id = qsRepository.findByNameExpected("server1-root").getId();
		Assert.assertEquals(3, qiRepository.findByNameExpected("server1").getStorages().size());
		Assert.assertEquals(8, qiRepository.count());
		Assert.assertEquals(4, qsRepository.count());
		em.flush();
		em.clear();

		resource.deleteAllStorages(subscription);

		// Check the exact new cost
		Assert.assertEquals(4236.084, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);

		// Check the associations
		Assert.assertNull(qsRepository.findOne(id));
		Assert.assertEquals(0, qiRepository.findByNameExpected("server1").getStorages().size());

		Assert.assertEquals(8, qiRepository.count());
		Assert.assertEquals(0, qsRepository.count());
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
		Assert.assertEquals(4392.414, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);

		// Check the associations
		Assert.assertNull(qsRepository.findOne(id));
		Assert.assertEquals(2, qiRepository.findByNameExpected("server1").getStorages().size());
	}

	private void refreshCost() {
		// Check the cost before updated cost (dummy value)
		Assert.assertEquals(0.128, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);

		// Check the cost fully updated and exact actual cost
		Assert.assertEquals(4396.614, resource.refreshCost(subscription), DELTA);
		Assert.assertEquals(4396.614, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
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
		Assert.assertEquals(4458.834, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assert.assertEquals("server1-bis", instance.getName());
		Assert.assertEquals(1024, instance.getRam().intValue());
		Assert.assertEquals(0.5, instance.getCpu(), DELTA);
		Assert.assertEquals(208.62, instance.getCost(), DELTA);
	}

	@Test
	public void createInstance() {
		refreshCost();

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setInstancePrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		final int id = resource.createInstance(vo);

		// Check the exact new cost
		Assert.assertEquals(4605.234, resource.getSusbcriptionStatus(subscription).getCost(), DELTA);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(id);
		Assert.assertEquals("serverZ", instance.getName());
		Assert.assertEquals("serverZD", instance.getDescription());
		Assert.assertEquals(1024, instance.getRam().intValue());
		Assert.assertEquals(0.5, instance.getCpu(), DELTA);
		Assert.assertEquals(208.62, instance.getCost(), DELTA);
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
		Assert.assertNotNull(quote);
		Assert.assertEquals(0.128, quote.getCost(), 0.0001);
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

	@Test(expected = NotImplementedException.class)
	public void linkAbstract() throws Exception {
		new AbstractProvResource() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		}.link(subscription);
	}

	@Test
	public void createAbstract() throws Exception {
		// Nothing to check
		new AbstractProvResource() {

			@Override
			public String getKey() {
				return "service:prov:sample";
			}
		}.create(subscription);
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

	@Test
	public void findInstancePriceTypeCriteria() {
		final TableItem<ProvInstancePriceType> tableItem = resource.findInstancePriceType(subscription,
				newUriInfo("deMand"));
		Assert.assertEquals(2, tableItem.getRecordsTotal());
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

	@Test
	public void findStorageType() {
		final TableItem<ProvStorageType> tableItem = resource.findStorageType(subscription, newUriInfo());
		Assert.assertEquals(2, tableItem.getRecordsTotal());
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
		Assert.assertEquals(1,
				resource.findStorageType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
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
		Assert.assertEquals(1,
				resource.findInstance(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
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
		final ComputedStoragePrice price = resource.lookupStorage(subscription, 2, null, ProvStorageOptimized.IOPS);

		// Check the storage result
		final ProvStorageType st = price.getType();
		Assert.assertNotNull(st.getId());
		Assert.assertEquals("storage1", st.getName());
		Assert.assertEquals(0.42, price.getCost(), DELTA);
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupStorageHighContraints() throws IOException {
		final ComputedStoragePrice price = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim()
						.writeValueAsString(resource.lookupStorage(subscription, 1024, ProvStorageFrequency.HOT, null)),
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
		Assert.assertNotNull(resource.lookupStorage(subscription, 512, ProvStorageFrequency.HOT, null));
		Assert.assertNotNull(resource.lookupStorage(subscription, 999, ProvStorageFrequency.HOT, null));
		Assert.assertNotNull(resource.lookupStorage(subscription, 512, ProvStorageFrequency.COLD, null));

		// Out of limits
		Assert.assertNull(resource.lookupStorage(subscription, 999, ProvStorageFrequency.COLD, null));
	}

	/**
	 * TODO Add in bootstrap
	 */
	protected UriInfo newUriInfo(final String criteria) {
		final UriInfo uriInfo = Mockito.mock(UriInfo.class);
		final MetadataMap<String, String> metadataMap = new MetadataMap<String, String>();
		metadataMap.putSingle(DataTableAttributes.SEARCH, criteria);
		Mockito.when(uriInfo.getQueryParameters()).thenReturn(metadataMap);
		return uriInfo;
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
		Assert.assertEquals(10242.944, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadDefaultHeader() throws IOException {
		resource.upload(subscription, new ClassPathResource("csv/upload-default.csv").getInputStream(), null, null, 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(18, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(17).getInstancePrice().getType().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(12).getInstancePrice().getInstance().getName());
		Assert.assertEquals(14, configuration.getStorages().size());
		Assert.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		Assert.assertEquals(10166.144, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadDefaultPriceType() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "constant" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand2", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance1",
				configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		Assert.assertEquals(128.228, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadFixedInstance() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "instance" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand2", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance10",
				configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		Assert.assertEquals(1874.048, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadFixedPriceType() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;on-demand1".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "priceType" },
				priceTypeRepository.findByNameExpected("on-demand2").getId(), 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("instance2",
				configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		Assert.assertEquals(135.548, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;LINUX".getBytes("UTF-8")), null, null, 1024,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		Assert.assertEquals(242610.548, configuration.getCost(), DELTA);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;1;64;LINUX".getBytes("UTF-8")), null, null, 1024,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assert.assertEquals(8, configuration.getInstances().size());
		Assert.assertEquals("on-demand1", configuration.getInstances().get(7).getInstancePrice().getType().getName());
		Assert.assertEquals("dynamic", configuration.getInstances().get(7).getInstancePrice().getInstance().getName());
		Assert.assertEquals(4, configuration.getStorages().size());
		Assert.assertEquals(451.04, configuration.getCost(), DELTA);
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadInstanceNotFound() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY;999;6;WINDOWS".getBytes("UTF-8")), null,
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1024, "UTF-8");
	}

	@Test(expected = ValidationJsonException.class)
	public void uploadStorageNotFound() throws IOException {
		resource.upload(subscription,
				new ByteArrayInputStream("ANY;1;1;LINUX;99999999999;HOT;THROUGHPUT".getBytes("UTF-8")), null,
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1, "UTF-8");
	}

	@Test(expected = BusinessException.class)
	public void uploadInvalidHeader() throws IOException {
		resource.upload(subscription, new ByteArrayInputStream("ANY".getBytes("UTF-8")), new String[] { "any" },
				priceTypeRepository.findByNameExpected("on-demand1").getId(), 1, "UTF-8");
	}
}
