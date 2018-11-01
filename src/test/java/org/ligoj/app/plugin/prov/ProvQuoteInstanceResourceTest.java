/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteInstanceResource}
 */
public class ProvQuoteInstanceResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookupInstance() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null,
				"Full Time 12 month", null, null);
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	public void lookupInstanceLicenseIncluded() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.WINDOWS, null, true,
				null, "Full Time 12 month", "INCLUDED", null);

		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C12", pi.getCode());
		Assertions.assertNull(pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	public void lookupInstanceLicenseByol() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.WINDOWS, null, true,
				null, "Full Time 12 month", "BYOL", null);

		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C120", pi.getCode());
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertEquals("BYOL", pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but software.
	 */
	@Test
	public void lookupInstanceSoftware() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.WINDOWS, null, true,
				null, "Full Time 12 month", null, "SQL Web");

		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C121", pi.getCode());
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertNull(pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but location.
	 */
	@Test
	public void lookupInstanceLocation() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true,
				"region-1", "Full Time 12 month", null, null);
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location different from the quote's one.
	 */
	@Test
	public void lookupInstanceLocationNotFoundButWorldwideService() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true,
				"region-2", "Full Time 12 month", null, null);
		checkInstance(lookup);
	}

	/**
	 * Search instance type within a region where minimal instance types are not available.
	 */
	@Test
	public void lookupInstanceLocationNotFound() {
		Assertions.assertEquals("instance2", qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true,
				"region-1", "Full Time 12 month", null, null).getPrice().getType().getName());

		final ProvLocation location = locationRepository.findByName("region-1");

		// Add location constraint on the first matching instances to exclude them
		ipRepository.findAllBy("type.name", "instance2").forEach(ip -> ip.setLocation(location));
		ipRepository.findAllBy("type.name", "dynamic").forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		// Instance 2 is not available in this region
		Assertions.assertEquals("instance4", qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true,
				"region-2", "Full Time 12 month", null, null).getPrice().getType().getName());
	}

	/**
	 * Search instance type within a non existing region
	 */
	@Test
	public void lookupLocationNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.lookup(subscription, 1, 2000, null,
				VmOs.LINUX, null, true, "region-xxx", "Full Time 12 month", null, null).getPrice().getType().getName());
	}

	private void checkInstance(final QuoteInstanceLookup lookup) {
		// Check the instance result
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu().intValue());
		Assertions.assertEquals(2000, pi.getType().getRam().intValue());
		Assertions.assertEquals("C11", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(102.48, pi.getCost(), DELTA);
		Assertions.assertEquals(1229.76, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("1y", pi.getTerm().getName());
		Assertions.assertEquals(102.48, lookup.getCost(), DELTA);
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupInstanceHighContraints() throws IOException {
		final QuoteInstanceLookup lookup = new ObjectMapperTrim()
				.readValue(
						new ObjectMapperTrim().writeValueAsString(qiResource.lookup(subscription, 3, 9, true,
								VmOs.WINDOWS, null, false, null, "Full Time 12 month", null, null)),
						QuoteInstanceLookup.class);
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance9", pi.getType().getName());
		Assertions.assertEquals(4, pi.getType().getCpu().intValue());
		Assertions.assertEquals(16000, pi.getType().getRam().intValue());
		Assertions.assertTrue(pi.getType().getConstant());
		Assertions.assertEquals(2928.0, pi.getCost(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertEquals("1y", pi.getTerm().getName());
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
		Assertions.assertNull(qiResource.lookup(subscription, 999, 0, false, VmOs.SUSE, null, true, null,
				"Full Time 12 month", null, null));
	}

	@Test
	public void lookupTypeNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.lookup(subscription, 999, 0, false, VmOs.SUSE, "any", true, null, null, null, null));
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupInstanceOnlyCustom() {
		final QuoteInstanceLookup lookup = qiResource.lookup(subscription, 999, 0, null, VmOs.LINUX, null, true, null,
				"Full Time 12 month", null, null);

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

		Assertions.assertEquals(242590.846, lookup.getCost(), DELTA);
	}

	/**
	 * This configuration suits to a custom instance.
	 */
	@Test
	public void lookupInstanceCustomIsCheaper() {
		assertPrice(qiResource.lookup(subscription, 1, 16000, null, VmOs.LINUX, null, true, null, "Dev", null, null),
				"C74", "dynamic", 146.842, "on-demand1");
	}

	/**
	 * Low usage rate, cheaper than 1y
	 */
	@Test
	public void lookupInstanceVariableDuration() {
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev", null, null),
				"C9", "instance2", 58.56, "on-demand2");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 11 month",
				null, null), "C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 12 month",
				null, null), "C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 13 month",
				null, null), "C9", "instance2", 117.12, "on-demand2");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 23 month",
				null, null), "C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 24 month",
				null, null), "C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Full Time 25 month",
				null, null), "C9", "instance2", 117.12, "on-demand2");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev 11 month", null,
				null), "C9", "instance2", 29.28, "on-demand2");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev 12 month", null,
				null), "C9", "instance2", 29.28, "on-demand2");
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev 13 month", null,
				null), "C9", "instance2", 29.28, "on-demand2");

		ProvUsage usage = usageRepository.findByName("Dev 11 month");
		usage.setRate(90);
		usageRepository.saveAndFlush(usage);
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev 11 month", null,
				null), "C9", "instance2", 105.408, "on-demand2");

		usage.setRate(98);
		usageRepository.saveAndFlush(usage);
		assertPrice(qiResource.lookup(subscription, 1, 2000, null, VmOs.LINUX, null, true, null, "Dev 11 month", null,
				null), "C11", "instance2", 102.48, "1y");

	}

	private void assertPrice(final QuoteInstanceLookup lookup, final String code, final String instance,
			final double cost, final String term) {
		// Check the custom instance
		final ProvInstancePrice pi = lookup.getPrice();
		Assertions.assertEquals(code, pi.getCode());
		Assertions.assertEquals(instance, pi.getType().getName());
		Assertions.assertEquals(term, pi.getTerm().getName());
		Assertions.assertEquals(cost, lookup.getCost(), DELTA);

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
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.update(vo));
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

		checkCost(qiResource.deleteAll(subscription), 2.73, 2.73, false);

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

		checkCost(qiResource.delete(id), 4092.358, 4092.358, false);

		// Check the exact new cost
		checkCost(subscription, 4092.358, 4092.358, false);
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
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMaxQuantity(null);
		final int id = qiResource.create(vo).getId();

		// Check the counter is now 1
		Assertions.assertEquals(1,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertTrue(repository.findBy("subscription.id", subscription).isUnboundCost());
		em.flush();
		em.clear();

		qiResource.delete(id);

		// Check the counter is back to 0
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
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
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		final UpdatedCost updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotalCost(), 4704.758, 7154.358, false);
		checkCost(updatedCost.getResourceCost(), 292.8, 1464, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().get("storage").size());

		// Check the cost is the same
		checkUpdatedCost();
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
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(null);
		UpdatedCost updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4398.558, 4398.558, true);
		checkCost(updatedCost.getResourceCost(), 146.4, 146.4, true);
		checkCost(subscription, 4398.558, 4398.558, true);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().get("storage").size());
		qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertEquals(4.2, updatedCost.getRelatedCosts().get("storage").get(qs.getId()).getMin(), DELTA);
		Assertions.assertEquals(4.2, updatedCost.getRelatedCosts().get("storage").get(qs.getId()).getMax(), DELTA);
		Assertions.assertTrue(updatedCost.getRelatedCosts().get("storage").get(qs.getId()).isUnbound());
		Assertions.assertTrue(qs.isUnboundCost());
		Assertions.assertEquals(4.2, qs.getCost(), DELTA);
		Assertions.assertEquals(4.2, qs.getMaxCost(), DELTA);

		// Check the cost is the same
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		qiResource.update(vo);
		checkUpdatedCost();
	}

	@Test
	public void updateInstanceIncommatibleOs() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setOs(VmOs.CENTOS);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.update(vo)),
				"os", "incompatible-os");
	}

	@Test
	public void updateInstance() {
		// Check the cost of related storages of this instance
		final Map<Integer, FloatingCost> storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		vo.setLocation("region-1");
		vo.setUsage("Full Time");
		final UpdatedCost updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId().intValue());

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 4460.778, 11460.758, false);
		checkCost(updatedCost.getResourceCost(), 208.62, 4172.4, false);
		checkCost(subscription, 4460.778, 11460.758, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelatedCosts().get("storage").size());

		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam().intValue());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(208.62, instance.getCost(), DELTA);
		Assertions.assertEquals(4172.4, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		final UpdatedCost updatedCost2 = qiResource.update(vo);
		checkCost(updatedCost2.getTotalCost(), 4356.468, 9374.558, false);
		checkCost(updatedCost2.getResourceCost(), 104.31, 2086.2, false);

		// Change the region of this instance, storage is also
		vo.setLocation("region-2");
	}

	@Test
	public void updateInstanceLocationNoMatchStorage() {
		// Add a storage only available in "region-1"
		final ProvQuoteStorage qs = new ProvQuoteStorage();
		qs.setPrice(spRepository.findBy("type.name", "storage4"));
		qs.setInstanceCompatible(true);
		qs.setLatency(Rate.BEST);
		qs.setQuoteInstance(qiRepository.findByName("server1"));
		qs.setName("qi-storage4");
		qs.setConfiguration(repository.findByName("quote1"));
		qs.setCost(0d);
		qs.setMaxCost(0d);
		qs.setSize(100);
		em.persist(qs);
		em.flush();

		// Check the cost
		checkCost(resource.refresh(subscription), 3469.4, 7135.0, false);

		// Everything identity but the region
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		vo.setLocation("region-1");

		// No change
		checkCost(qiResource.update(vo).getTotalCost(), 3469.4, 7135.0, false);

		// "C1" -> "C7"
		checkCost(resource.refresh(subscription), 3447.44, 7025.2, false);
		vo.setPrice(ipRepository.findByExpected("code", "C7").getId());

		// No change
		checkCost(qiResource.update(vo).getTotalCost(), 3447.44, 7025.2, false);
		checkCost(resource.refresh(subscription), 3447.44, 7025.2, false);

		// Check the update failed because of "storage4"
		vo.setLocation("region-2"); // "region-1" to "region-2"
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.update(vo)),
				"storage", "no-match-storage");
	}

	@Test
	public void updateInstanceLocation() {
		logQuote();

		// Engage first optimization
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		logQuote();

		// Everything identity but the region
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);

		// Check the exact new cost, same as initial
		checkCost(qiResource.update(vo).getTotalCost(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C7"
		checkCost(resource.refresh(subscription), 3143.44, 5505.2, false);
		logQuote();

		vo.setLocation("region-2"); // "region-1" -> "region-2"
		// Storage "server1-data" price changed for "region-2"
		// Storage "server1-temp" price changed for "region-2"
		checkCost(qiResource.update(vo).getTotalCost(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C7"
		checkCost(resource.refresh(subscription), 3164.32, 5609.6, false);
		logQuote();
	}

	private void logQuote() {
		final QuoteVo vo3 = resource.getConfiguration(subscription);
		vo3.getInstances().forEach(q -> log.info(q.getName() + " -cost " + q.getCost() + " -type "
				+ q.getPrice().getType().getName() + " -code " + q.getPrice().getCode()));
		vo3.getStorages().forEach(
				q -> log.info(q.getName() + " -cost " + q.getCost() + " - type " + q.getPrice().getType().getName()));
	}

	@Test
	public void updateInstanceOsCompatible() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server2").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C9").getId());
		vo.setName("server2-bis");
		vo.setOs(VmOs.CENTOS);
		vo.setRam(1024);
		vo.setCpu(0.5);
		qiResource.update(vo);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals(VmOs.CENTOS, instance.getOs());
	}

	@Test
	public void createInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
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
		final UpdatedCost updatedCost = qiResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6790.958, 10283.658, false);
		checkCost(updatedCost.getResourceCost(), 2086.2, 3129.3, false);
		Assertions.assertEquals(1, updatedCost.getRelatedCosts().size());
		Assertions.assertTrue(updatedCost.getRelatedCosts().get("storage").isEmpty());
		checkCost(subscription, 6790.958, 10283.658, false);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertTrue(instance.isEphemeral());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals(1024, instance.getRam().intValue());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assertions.assertEquals(2086.2, instance.getCost(), DELTA);
		Assertions.assertEquals(3129.3, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assertions.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());
	}

	@Test
	public void createInstanceIncompatibleOs() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
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
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.create(vo)),
				"os", "incompatible-os");
	}

	@Test
	public void createUnboundInstance() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(null);
		final UpdatedCost updatedCost = qiResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotalCost(), 6790.958, 9240.558, true);
		checkCost(updatedCost.getResourceCost(), 2086.2, 2086.2, true);
		Assertions.assertTrue(updatedCost.getRelatedCosts().get("storage").isEmpty());
		checkCost(subscription, 6790.958, 9240.558, true);
		final ProvQuoteInstance instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertNull(instance.getMaxVariableCost());
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertNull(instance.getMaxQuantity());
		Assertions.assertTrue(instance.isUnboundCost());
	}

	@Test
	public void createInstanceMinGreaterThanMax() {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(100);
		vo.setMaxQuantity(10);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.create(vo)),
				"maxQuantity", "Min");
	}

	@Test
	public void findInstanceTerms() {
		final TableItem<ProvInstancePriceTerm> tableItem = qiResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTermsCriteria() {
		final TableItem<ProvInstancePriceTerm> tableItem = qiResource.findPriceTerms(subscription,
				newUriInfo("deMand"));
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstancePriceTermsNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qiResource.findPriceTerms(-1, newUriInfo()));
	}

	@Test
	public void findInstancePriceTermsAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findPriceTerms(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findPriceTerms(subscription, newUriInfo()));
	}

	@Test
	public void findLicenses() {
		final List<String> tableItem = qiResource.findLicenses(subscription, VmOs.WINDOWS);
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("INCLUDED", tableItem.get(0));
		Assertions.assertEquals("BYOL", tableItem.get(1));
	}

	@Test
	public void findLicensesNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findLicenses(subscription, VmOs.WINDOWS));
	}

	@Test
	public void findSoftwares() {
		final List<String> tableItem = qiResource.findSoftwares(subscription, VmOs.WINDOWS);
		Assertions.assertEquals(1, tableItem.size());
		Assertions.assertEquals("SQL Web", tableItem.get(0));
	}

	@Test
	public void findSoftwaresNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findSoftwares(subscription, VmOs.WINDOWS));
	}

	@Test
	public void findInstance() {
		final TableItem<ProvInstanceType> tableItem = qiResource.findAllTypes(subscription, newUriInfo());
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findInstanceCriteria() {
		final TableItem<ProvInstanceType> tableItem = qiResource.findAllTypes(subscription, newUriInfo("sTance1"));
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
		Assertions.assertEquals("instance10", tableItem.getData().get(1).getName());
	}

	@Test
	public void findInstanceNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qiResource.findAllTypes(-1, newUriInfo()));
	}

	@Test
	public void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findAllTypes(subscription, newUriInfo()));
	}
}
