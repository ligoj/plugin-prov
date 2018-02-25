package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

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
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
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
public class ProvQuoteInstanceResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource iResource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	private int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class,
						ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		refreshCost();
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
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupInstance() {
		final QuoteInstanceLookup lookup = iResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, null, true,
				null, null);
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location.
	 */
	@Test
	public void lookupInstanceLocation() {
		final QuoteInstanceLookup lookup = iResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, null, true,
				"region-1", null);
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location different from the quote's one.
	 */
	@Test
	public void lookupInstanceLocationNotFoundButWorldwideService() {
		final QuoteInstanceLookup lookup = iResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, null, true,
				"region-xxx", null);
		checkInstance(lookup);
	}

	/**
	 * Search instance type within a region where minimal instance types are not available.
	 */
	@Test
	public void lookupInstanceLocationNotFound() {
		Assertions.assertEquals("instance2",
				iResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-xxx", null)
						.getPrice().getType().getName());

		final ProvLocation location = locationRepository.findByName("region-1");

		// Add location constraint on the first matching instances to exclude
		// them
		ipRepository.findAllBy("type.name", "instance2").forEach(ip -> ip.setLocation(location));
		ipRepository.findAllBy("type.name", "dynamic").forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		// Instance 2 is not available in this region
		Assertions.assertEquals("instance4",
				iResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, null, true, "region-xxx", null)
						.getPrice().getType().getName());
	}

	private void checkInstance(final QuoteInstanceLookup lookup) {
		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu().intValue());
		Assertions.assertEquals(2000, pi.getType().getRam().intValue());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(0.14, pi.getCost(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("1y", pi.getTerm().getName());
		Assertions.assertEquals(102.2, lookup.getCost(), DELTA);
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupInstanceHighContraints() throws IOException {
		final QuoteInstanceLookup lookup = new ObjectMapperTrim().readValue(new ObjectMapperTrim().writeValueAsString(
				iResource.lookup(subscription, 3, 9, true, VmOs.WINDOWS, null, "on-demand1", false, null, null)),
				QuoteInstanceLookup.class);
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance9", pi.getType().getName());
		Assertions.assertEquals(4, pi.getType().getCpu().intValue());
		Assertions.assertEquals(16000, pi.getType().getRam().intValue());
		Assertions.assertTrue(pi.getType().getConstant());
		Assertions.assertEquals(5.6, pi.getCost(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertFalse(pi.getType().isCustom());

		// Not serialized
		Assertions.assertNull(pi.getType().getNode());
		Assertions.assertNull(pi.getTerm().getNode());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceNoMatch() {
		Assertions.assertNull(iResource.lookup(subscription, 999, 0, false, VmOs.SUSE, null, "1y", true, null, null));
	}

	@Test
	public void lookupTermNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.lookup(subscription, 999, 0, false, VmOs.SUSE, null, "any", true, null, null);
		});
	}

	@Test
	public void lookupTypeNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.lookup(subscription, 999, 0, false, VmOs.SUSE, "any", "1y", true, null, null);
		});
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceOnlyCustom() {
		final QuoteInstanceLookup lookup = iResource.lookup(subscription, 999, 0, null, VmOs.LINUX, null, null, true,
				null, null);

		// Check the custom instance
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("dynamic", pi.getType().getName());
		Assertions.assertEquals(0, pi.getType().getCpu().intValue());
		Assertions.assertEquals(0, pi.getType().getRam().intValue());
		Assertions.assertTrue(pi.getType().getConstant());
		Assertions.assertEquals(0, pi.getCost(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertTrue(pi.getType().isCustom());

		Assertions.assertEquals(241928.03, lookup.getCost(), DELTA);
	}

	@Test
	public void updateInstanceNonVisibleInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findBy("type.name", "instanceX").getId());
		vo.setName("server1-bis");
		vo.setRam(1);
		vo.setCpu(0.5);
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.update(vo);
		});
	}

	@Test
	public void deleteAllInstances() {
		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storage2));
		Assertions.assertTrue(qsRepository.existsById(storage3));
		Assertions.assertEquals(8, qiRepository.count());
		em.flush();
		em.clear();

		checkCost(iResource.deleteAll(subscription), 2.73, 2.73, false);

		// Check the exact new cost
		checkCost(subscription, 2.73, 2.73, false);
		Assertions.assertNull(qiRepository.findOne(id));
		Assertions.assertEquals(1, qiRepository.count());

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertFalse(qsRepository.existsById(storage2));
		Assertions.assertFalse(qsRepository.existsById(storage3));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	public void deleteInstance() {
		final Integer id = qiRepository.findByNameExpected("server1").getId();
		final Integer storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final Integer storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final Integer storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final Integer storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storage2));
		Assertions.assertTrue(qsRepository.existsById(storage3));
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertFalse(repository.findBy("subscription.id", subscription).isUnboundCost());

		em.flush();
		em.clear();

		checkCost(iResource.delete(id), 4081.185, 4081.185, false);

		// Check the exact new cost
		checkCost(subscription, 4081.185, 4081.185, false);
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertNull(qiRepository.findOne(id));

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertFalse(qsRepository.existsById(storage2));
		Assertions.assertFalse(qsRepository.existsById(storage3));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	public void deleteUnboundInstance() {
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMaxQuantity(null);
		final int id = iResource.create(vo).getId();

		// Check the counter is now 1
		Assertions.assertEquals(1,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertTrue(repository.findBy("subscription.id", subscription).isUnboundCost());
		em.flush();
		em.clear();

		iResource.delete(id);

		// Check the counter is back to 0
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
	}

	private void refreshCost() {

		// Check the cost fully updated and exact actual cost
		final FloatingCost cost = resource.refreshCost(subscription);
		Assertions.assertEquals(4692.785, cost.getMin(), DELTA);
		Assertions.assertEquals(7139.185, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4692.785, 7139.185, false);
		em.flush();
		em.clear();
	}

	private Map<Integer, FloatingCost> toStoragesFloatingCost(final String instanceName) {
		return qsRepository.findAllBy("quoteInstance.name", instanceName).stream().collect(Collectors.toMap(
				ProvQuoteStorage::getId,
				qs -> new FloatingCost(qs.getCost(), qs.getMaxCost(), qs.getQuoteInstance().getMaxQuantity() == null)));
	}

	@Test
	public void updateInstanceIdentity() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.2).getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		final UpdatedCost updatedCost = iResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4692.785, 7139.185, false);
		checkCost(updatedCost.getResourceCost(), 292, 1460, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().size());

		// Check the cost is the same
		refreshCost();
	}

	@Test
	public void updateInstanceUnbound() {
		ProvQuoteStorage qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertFalse(qs.isUnboundCost());
		Assertions.assertEquals(8.4, qs.getCost(), DELTA);
		Assertions.assertEquals(42, qs.getMaxCost(), DELTA);

		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("cost", 0.2).getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(null);
		UpdatedCost updatedCost = iResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4386.985, 4386.985, true);
		checkCost(updatedCost.getResourceCost(), 146, 146, true);
		checkCost(subscription, 4386.985, 4386.985, true);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().size());
		qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertEquals(4.2, updatedCost.getRelatedCosts().get(qs.getId()).getMin(), DELTA);
		Assertions.assertEquals(4.2, updatedCost.getRelatedCosts().get(qs.getId()).getMax(), DELTA);
		Assertions.assertTrue(updatedCost.getRelatedCosts().get(qs.getId()).isUnbound());
		Assertions.assertTrue(qs.isUnboundCost());
		Assertions.assertEquals(4.2, qs.getCost(), DELTA);
		Assertions.assertEquals(4.2, qs.getMaxCost(), DELTA);

		// Check the cost is the same
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		updatedCost = iResource.update(vo);
		refreshCost();
	}

	@Test
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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.update(vo);
		}), "os", "incompatible-os");
	}

	@Test
	public void updateInstance() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

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
		vo.setUsage("Full Time");
		final UpdatedCost updatedCost = iResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4449.035, 11438.185, false);
		checkCost(updatedCost.getResourceCost(), 208.05, 4161, false);
		checkCost(subscription, 4449.035, 11438.185, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().size());

		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam().intValue());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(208.05, instance.getCost(), DELTA);
		Assertions.assertEquals(4161, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		final UpdatedCost updatedCost2 = iResource.update(vo);
		checkCost(updatedCost2.getTotalCost(), 4345.01, 9357.685, false);
		checkCost(updatedCost2.getResourceCost(), 104.025, 2080.5, false);
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
		iResource.update(vo);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals(VmOs.CENTOS, instance.getOs());
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
		final UpdatedCost updatedCost = iResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6773.285, 10259.935, false);
		checkCost(updatedCost.getResourceCost(), 2080.5, 3120.75, false);
		Assertions.assertTrue(updatedCost.getRelatedCosts().isEmpty());
		checkCost(subscription, 6773.285, 10259.935, false);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertTrue(instance.isEphemeral());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals(1024, instance.getRam().intValue());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assertions.assertEquals(2080.5, instance.getCost(), DELTA);
		Assertions.assertEquals(3120.75, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assertions.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(10, instance.getMinQuantity().intValue());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());
	}

	@Test
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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.create(vo);
		}), "os", "incompatible-os");
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
		final UpdatedCost updatedCost = iResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6773.285, 9219.685, true);
		checkCost(updatedCost.getResourceCost(), 2080.5, 2080.5, true);
		Assertions.assertTrue(updatedCost.getRelatedCosts().isEmpty());
		checkCost(subscription, 6773.285, 9219.685, true);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertNull(instance.getMaxVariableCost());
		Assertions.assertEquals(10, instance.getMinQuantity().intValue());
		Assertions.assertNull(instance.getMaxQuantity());
		Assertions.assertTrue(instance.isUnboundCost());
	}

	@Test
	public void createInstanceMinGreaterThanMax() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("cost", 0.285).getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(100);
		vo.setMaxQuantity(10);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.create(vo);
		}), "maxQuantity", "Min");
	}

	@Test
	public void findInstanceTerm() {
		final TableItem<ProvInstancePriceTerm> tableItem = iResource.findPriceTerm(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTermCriteria() {
		final TableItem<ProvInstancePriceTerm> tableItem = iResource.findPriceTerm(subscription, newUriInfo("deMand"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTermNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> {
			iResource.findPriceTerm(-1, newUriInfo());
		});
	}

	@Test
	public void findInstancePriceTermAnotherSubscription() {
		Assertions.assertEquals(1,
				iResource.findPriceTerm(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findInstancePriceTermNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.findPriceTerm(subscription, newUriInfo());
		});
	}

	@Test
	public void findInstance() {
		final TableItem<ProvInstanceType> tableItem = iResource.findAll(subscription, newUriInfo());
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstanceCriteria() {
		final TableItem<ProvInstanceType> tableItem = iResource.findAll(subscription, newUriInfo("sTance1"));
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
		Assertions.assertEquals("instance10", tableItem.getData().get(1).getName());
	}

	@Test
	public void findInstanceNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> {
			iResource.findAll(-1, newUriInfo());
		});
	}

	@Test
	public void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				iResource.findAll(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.findAll(subscription, newUriInfo());
		});
	}

	@Test
	public void upload() throws IOException {
		iResource.upload(subscription, new ClassPathResource("csv/upload.csv").getInputStream(),
				new String[] { "name", "cpu", "ram", "disk", "latency", "os", "constant" }, false, null, 1, "UTF-8");
		checkUpload();
	}

	@Test
	public void uploadIncludedHeaders() throws IOException {
		iResource.upload(subscription, new ClassPathResource("csv/upload-with-headers.csv").getInputStream(), null,
				true, null, 1, "UTF-8");
		checkUpload();
	}

	private void checkUpload() {
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(18, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assertions.assertEquals(15, configuration.getStorages().size());
		Assertions.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14546.049, 16992.449, false);
	}

	@Test
	public void uploadDefaultHeader() throws IOException {
		iResource.upload(subscription, new ClassPathResource("csv/upload-default.csv").getInputStream(), null, false,
				null, 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(18, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(17).getPrice().getTerm().getName());
		Assertions.assertEquals(1, configuration.getInstances().get(17).getMinQuantity().intValue());
		Assertions.assertEquals(1, configuration.getInstances().get(17).getMaxQuantity().intValue());
		Assertions.assertNull(configuration.getInstances().get(17).getMaxVariableCost());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(12).getPrice().getType().getName());
		Assertions.assertEquals(14, configuration.getStorages().size());
		Assertions.assertNotNull(configuration.getStorages().get(13).getQuoteInstance());
		checkCost(configuration.getCost(), 14539.329, 16985.729, false);
	}

	@Test
	public void uploadDefaultPriceTerm() throws IOException {
		iResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;true;true;region-1;Full Time".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "constant", "ephemeral", "location", "usage" }, false,
				"on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvInstancePrice instancePrice = configuration.getInstances().get(7).getPrice();
		final ProvInstancePriceTerm ipt = instancePrice.getTerm();
		Assertions.assertEquals("on-demand2", ipt.getName());
		Assertions.assertTrue(ipt.isEphemeral());
		Assertions.assertTrue(ipt.isVariable());
		Assertions.assertEquals("instance1", instancePrice.getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.535, 7266.935, false);
	}

	@Test
	public void uploadFixedInstance() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "ephemeral" }, false, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvInstancePriceTerm term = configuration.getInstances().get(7).getPrice().getTerm();
		Assertions.assertEquals("on-demand2", term.getName());
		Assertions.assertEquals("instance10", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadBoundQuantities() throws IOException {
		iResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1000;true".getBytes("UTF-8")), new String[] {
						"name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity", "ephemeral" },
				false, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity().intValue());
		Assertions.assertTrue(qi.getPrice().getTerm().isEphemeral());
		Assertions.assertTrue(qi.getPrice().getTerm().isVariable());
		Assertions.assertEquals(1000, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 135099.185, false);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 210, false);
	}

	@Test
	public void uploadMaxQuantities() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;1;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity",
						"ephemeral" },
				false, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity().intValue());
		Assertions.assertEquals(1, qi.getMaxQuantity().intValue());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 7267.145, false);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, false);
	}

	@Test
	public void uploadUnBoundQuantities() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;1;true;1;0;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "disk", "constant", "minQuantity", "maxQuantity",
						"ephemeral" },
				false, "on-demand2", 1, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance qi = configuration.getInstances().get(7);
		Assertions.assertEquals(1, qi.getMinQuantity().intValue());
		Assertions.assertNull(qi.getMaxQuantity());
		Assertions.assertEquals(5, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4820.745, 7267.145, true);
		final Map<Integer, FloatingCost> storagesFloatingCost = toStoragesFloatingCost("ANY");
		Assertions.assertEquals(1, storagesFloatingCost.size());
		checkCost(storagesFloatingCost.values().iterator().next(), 0.21, 0.21, true);
	}

	@Test
	public void uploadInternetAccess() throws IOException {
		iResource.upload(subscription,
				new ByteArrayInputStream("ANY;0.5;500;LINUX;instance10;PUBLIC;true".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "type", "internet", "ephemeral" }, false, "on-demand2", 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("instance10", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(InternetAccess.PUBLIC, configuration.getInstances().get(7).getInternet());
		checkCost(configuration.getCost(), 6561.585, 9007.985, false);
	}

	@Test
	public void uploadFixedPriceTerm() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;on-demand1;66".getBytes("UTF-8")),
				new String[] { "name", "cpu", "ram", "os", "term", "maxVariableCost" }, false, "on-demand2", 1,
				"UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		final ProvQuoteInstance instance = configuration.getInstances().get(7);
		final ProvInstancePrice instancePrice = instance.getPrice();
		Assertions.assertEquals("on-demand1", instancePrice.getTerm().getName());
		Assertions.assertFalse(instancePrice.getTerm().isEphemeral());
		Assertions.assertFalse(instancePrice.getTerm().isVariable());
		Assertions.assertEquals("instance2", instancePrice.getType().getName());
		Assertions.assertEquals(66, instance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 4827.835, 7274.235, false);
	}

	@Test
	public void uploadOnlyCustomFound() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;999;6;LINUX".getBytes("UTF-8")), null, false, null,
				1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 246640.288, 249086.688, false);
	}

	@Test
	public void uploadCustomLowest() throws IOException {
		iResource.upload(subscription, new ByteArrayInputStream("ANY;1;64;LINUX".getBytes("UTF-8")), null, false, null,
				1024, "UTF-8");
		final QuoteVo configuration = resource.getConfiguration(subscription);
		Assertions.assertEquals(8, configuration.getInstances().size());
		Assertions.assertEquals("on-demand1", configuration.getInstances().get(7).getPrice().getTerm().getName());
		Assertions.assertEquals("dynamic", configuration.getInstances().get(7).getPrice().getType().getName());
		Assertions.assertEquals(4, configuration.getStorages().size());
		checkCost(configuration.getCost(), 5142.672, 7589.072, false);
	}

	/**
	 * Expected usage does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	public void uploadInvalidUsageForSubscription() {
		Assertions.assertEquals("Full Time2", Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;Full Time2".getBytes("UTF-8")),
					new String[] { "name", "cpu", "ram", "os", "usage" }, false, "on-demand2", 1, "UTF-8");
		}).getMessage());
	}

	/**
	 * Expected location does not exist for this subscription, so there is no matching instance.
	 */
	@Test
	public void uploadInvalidLocationForSubscription() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;region-3".getBytes("UTF-8")),
					new String[] { "name", "cpu", "ram", "os", "location" }, false, "on-demand2", 1, "UTF-8");
		}), "instance", "no-match-instance");
	}

	/**
	 * Expected location does not exist at all?
	 */
	@Test
	public void uploadInvalidLocation() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;region-ZZ".getBytes("UTF-8")),
					new String[] { "name", "cpu", "ram", "os", "location" }, false, "on-demand2", 1, "UTF-8");
		}), "instance", "no-match-instance");
	}

	/**
	 * Expected usage does not exist at all.
	 */
	@Test
	public void uploadInvalidUsage() {
		Assertions.assertEquals("any", Assertions.assertThrows(EntityNotFoundException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY;0.5;500;LINUX;any".getBytes("UTF-8")),
					new String[] { "name", "cpu", "ram", "os", "usage" }, false, "on-demand2", 1, "UTF-8");
		}).getMessage());
	}

	@Test
	public void uploadInstanceNotFound() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY;999;6;WINDOWS".getBytes("UTF-8")), null, false,
					"on-demand1", 1024, "UTF-8");
		}), "instance", "no-match-instance");
	}

	@Test
	public void uploadStorageNotFound() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.upload(subscription,
					new ByteArrayInputStream("ANY;1;1;LINUX;99999999999;BEST;THROUGHPUT".getBytes("UTF-8")),
					new String[] { "name", "cpu", "ram", "os", "disk", "latency", "optimized" }, false, "on-demand1", 1,
					"UTF-8");
		}), "storage", "NotNull");
	}

	@Test
	public void uploadInvalidHeader() {
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> {
			iResource.upload(subscription, new ByteArrayInputStream("ANY".getBytes("UTF-8")), new String[] { "any" },
					false, "on-demand1", 1, "UTF-8");
		}), "headers", "invalid-header");
	}
}
