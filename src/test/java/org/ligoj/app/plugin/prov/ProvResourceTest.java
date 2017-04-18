package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTypeRepository;
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
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.fasterxml.jackson.core.JsonParseException;

/**
 * Test class of {@link ProvResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvResourceTest extends AbstractAppTest {

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvInstancePriceTypeRepository priceTypeRepository;

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
		Assert.assertEquals(11, status.getTotalCpu());
		Assert.assertEquals(22000, status.getTotalRam());
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
		Assert.assertEquals(0, status.getTotalCpu());
		Assert.assertEquals(0, status.getTotalRam());
		Assert.assertEquals(0, status.getNbStorages());
		Assert.assertEquals(0, status.getTotalStorage());
	}

	@Test
	public void getConfiguration() {
		final QuoteVo vo = resource.getConfiguration(subscription);
		Assert.assertEquals("quote1", vo.getName());
		Assert.assertEquals("quoteD1", vo.getDescription());
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
		final ProvInstancePrice instancePrice = quoteInstance.getInstance();
		Assert.assertEquals(0.2, instancePrice.getCost(), 0.001);
		Assert.assertEquals(VmOs.LINUX, instancePrice.getOs());
		Assert.assertNotNull(instancePrice.getType().getId());
		Assert.assertEquals(15, instancePrice.getType().getPeriod().intValue());
		Assert.assertEquals("on-demand1", instancePrice.getType().getName());
		Assert.assertEquals("15 minutes fragment", instancePrice.getType().getDescription());
		final ProvInstance instance = instancePrice.getInstance();
		Assert.assertNotNull(instance.getId().intValue());
		Assert.assertEquals("instance1", instance.getName());
		Assert.assertEquals("instanceD1", instance.getDescription());
		Assert.assertEquals(1, instance.getCpu().intValue());
		Assert.assertEquals(2000, instance.getRam().intValue());
		Assert.assertTrue(instance.getConstant());

		// Check storage
		final List<ProvQuoteStorageVo> storages = vo.getStorages();
		Assert.assertEquals(4, storages.size());
		final ProvQuoteStorageVo quoteStorage = storages.get(0);
		Assert.assertNotNull(quoteStorage.getId());
		Assert.assertEquals("server1-root", quoteStorage.getName());
		Assert.assertEquals("server1-rootD", quoteStorage.getDescription());
		Assert.assertEquals(20, quoteStorage.getSize());
		Assert.assertNotNull(quoteStorage.getInstance());
		final ProvStorage storage = quoteStorage.getStorage();
		Assert.assertNotNull(storage.getId());
		Assert.assertEquals(0.21, storage.getCost(), 0.001);
		Assert.assertEquals("storage1", storage.getName());
		Assert.assertEquals("storageD1", storage.getDescription());
		Assert.assertEquals(VmStorageType.HOT, storage.getType());

		// Not attached storage
		Assert.assertNull(storages.get(3).getInstance());

	}

	@Test
	public void getConfigurationEmpty() {
		final QuoteVo vo = resource.getConfiguration(getSubscription("mda", ProvResource.SERVICE_KEY));
		Assert.assertEquals("quote2", vo.getName());
		Assert.assertEquals("quoteD2", vo.getDescription());
		Assert.assertNotNull(vo.getId());

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
		final ProvInstancePrice pi = resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 0, 0,
				false, VmOs.LINUX, null);
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance2", pi.getInstance().getName());
		Assert.assertEquals(1, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(2000, pi.getInstance().getRam().intValue());
		Assert.assertEquals(0.14, pi.getCost(), 0.001);
		Assert.assertEquals(VmOs.LINUX, pi.getOs());
		Assert.assertEquals("1y", pi.getType().getName());
	}

	/**
	 * Advanced case, all requirements.
	 * 
	 * @throws IOException
	 * @throws @throws
	 *             JsonMappingException
	 * @throws JsonParseException
	 */
	@Test
	public void findInstanceHighContraints() throws IOException {
		final ProvInstancePrice pi = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim()
						.writeValueAsString(resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 3,
								9, true, VmOs.WINDOWS, priceTypeRepository.findByName("on-demand1").getId())),
				ProvInstancePrice.class);
		Assert.assertNotNull(pi.getId());
		Assert.assertEquals("instance9", pi.getInstance().getName());
		Assert.assertEquals(4, pi.getInstance().getCpu().intValue());
		Assert.assertEquals(16000, pi.getInstance().getRam().intValue());
		Assert.assertTrue(pi.getInstance().getConstant());
		Assert.assertEquals(5.6, pi.getCost(), 0.001);
		Assert.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assert.assertEquals("on-demand1", pi.getType().getName());

		// Not serialized
		Assert.assertNull(pi.getInstance().getNode());
		Assert.assertNull(pi.getType().getNode());
	}

	/**
	 * Too much requirements
	 */
	@Test(expected = ValidationJsonException.class)
	public void findInstanceNoMatch() {
		resource.findInstance(getSubscription("mda", ProvResource.SERVICE_KEY), 999, 0, false, VmOs.LINUX, null);
	}

	@Test
	public void getKey() {
		Assert.assertEquals("service:prov", resource.getKey());
	}

}
