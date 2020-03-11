/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import static org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceQuery.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteInstanceResource}
 */
public class ProvQuoteInstanceResourceTest extends AbstractProvResourceTest {

	private static final String FULL = "Full Time 12 month";

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
	 * Builder coverage
	 */
	@Test
	void queryJson() throws IOException {
		new ObjectMapperTrim().readValue("{\"software\":\"S\",\"ephemeral\":true,"
				+ "\"cpu\":2,\"ram\":3000,\"constant\":true,\"license\":\"LI\",\"os\":\"LINUX\","
				+ "\"location\":\"L\",\"usage\":\"U\",\"type\":\"T\"}", QuoteInstanceQuery.class);
		QuoteInstanceQuery.builder().toString();
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookup() {
		final var lookup = qiResource.lookup(subscription, builder().ram(2000).ephemeral(true).usage(FULL).build());
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	void lookupLicenseIncluded() {
		final var lookup = qiResource.lookup(subscription,
				builder().ram(2000).os(VmOs.WINDOWS).usage(FULL).license("INCLUDED").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C12", pi.getCode());
		Assertions.assertNull(pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but license.
	 */
	@Test
	void lookupLicenseByol() {
		final var lookup = qiResource.lookup(subscription,
				builder().ram(2000).os(VmOs.WINDOWS).usage(FULL).license("BYOL").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C120", pi.getCode());
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertEquals("BYOL", pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but software.
	 */
	@Test
	void lookupSoftware() {
		final var lookup = qiResource.lookup(subscription, builder().os(VmOs.WINDOWS).software("SQL Web").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals("C121", pi.getCode());
		Assertions.assertEquals(VmOs.WINDOWS, pi.getOs());
		Assertions.assertNull(pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but software.
	 */
	@Test
	void lookupMax() {
		repository.findByName("quote1").setReservationMode(ReservationMode.MAX);
		final var build = builder().ramMax(2000).cpuMax(2d).build();
		build.setCpuMax(2d); // Only for coverage
		build.setRamMax(2000); // Only for coverage
		final var lookup = qiResource.lookup(subscription, build);

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance4", pi.getType().getName());
		Assertions.assertEquals("C19", pi.getCode());
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertNull(pi.getLicense());
	}

	/**
	 * Basic case, almost no requirements but processor.
	 */
	@Test
	void lookupProcessor() {
		final var build = builder().processor("Intel Xeon").build();
		build.setProcessor("Intel Xeon"); // Coverage only
		final var lookup = qiResource.lookup(subscription, build);

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance11", pi.getType().getName());
		Assertions.assertEquals("C61", pi.getCode());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", pi.getType().getProcessor());
	}

	/**
	 * Basic case, almost no requirements but physical/metal.
	 */
	@Test
	void lookupPhysical() {
		final var build = builder().physical(true).build();
		build.setPhysical(true); // Coverage only
		final var lookup = qiResource.lookup(subscription, build);

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("instance7", pi.getType().getName());
		Assertions.assertEquals("C37", pi.getCode());
		Assertions.assertTrue(pi.getType().getPhysical());
	}

	/**
	 * Basic case, almost no requirements but processor with 'contains'.
	 */
	@Test
	void lookupProcessorContains() {
		final var lookup = qiResource.lookup(subscription, builder().processor("epyc 7571").build());

		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertEquals("dynamic", pi.getType().getName());
		Assertions.assertEquals("C74", pi.getCode());
		Assertions.assertEquals("AMD EPYC 7571", pi.getType().getProcessor());
	}

	/**
	 * Basic case, almost no requirements but location.
	 */
	@Test
	void lookupLocation() {
		final var lookup = qiResource.lookup(subscription,
				builder().ram(2000).location("region-1").usage(FULL).build());
		checkInstance(lookup);
	}

	/**
	 * Basic case, almost no requirements but location different from the quote's one.
	 */
	@Test
	void lookupLocationNotFoundButWorldwideService() {
		final var lookup = qiResource.lookup(subscription,
				builder().ram(2000).usage(FULL).ephemeral(true).location("region-2").build());
		checkInstance(lookup);
	}

	/**
	 * Search instance type within a region where minimal instance types are not available.
	 */
	@Test
	void lookupNoMatchAtLocation() {
		Assertions.assertEquals("instance2",
				qiResource.lookup(subscription, builder().ram(2000).usage(FULL).location("region-1").build()).getPrice()
						.getType().getName());

		final var location = locationRepository.findByName("region-1");

		// Add location constraint on the first matching instances to exclude them
		ipRepository.findAllBy("type.name", "instance2").forEach(ip -> ip.setLocation(location));
		ipRepository.findAllBy("type.name", "dynamic").forEach(ip -> ip.setLocation(location));
		em.flush();
		em.clear();

		// Instance 2 is not available in this region
		Assertions.assertEquals("instance4",
				qiResource.lookup(subscription, builder().ram(2000).usage(FULL).location("region-2").build()).getPrice()
						.getType().getName());
	}

	/**
	 * Search instance type within a non existing region
	 */
	@Test
	void lookupLocationNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.lookup(subscription, builder().location("region-xxx").build()));
	}

	private void checkInstance(final QuoteInstanceLookup lookup) {
		// Check the instance result
		final var pi = lookup.getPrice();
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
	void lookupHighConstraints() throws IOException {
		final var lookup = new ObjectMapperTrim().readValue(
				new ObjectMapperTrim().writeValueAsString(qiResource.lookup(subscription,
						builder().cpu(3).ram(9).constant(true).os(VmOs.WINDOWS).usage(FULL).build())),
				QuoteInstanceLookup.class);
		final var pi = lookup.getPrice();
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
	 * Advanced case, all requirements.
	 */
	@Test
	void lookupTermConstraints() throws IOException {
		final var lookup = qiResource.lookup(subscription,
				builder().cpu(1).usage("Full Time global").location("region-5").build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance5", pi.getType().getName());
		Assertions.assertEquals(1537.2, pi.getCost(), DELTA);
		Assertions.assertEquals("on-demandR5", pi.getTerm().getName());
		Assertions.assertEquals("region-5", pi.getTerm().getLocation().getName());
		Assertions.assertFalse(pi.getTerm().getConvertibleEngine());
		Assertions.assertFalse(pi.getTerm().getConvertibleOs());
		Assertions.assertTrue(pi.getTerm().getConvertibleFamily());
		Assertions.assertTrue(pi.getTerm().getConvertibleType());
		Assertions.assertTrue(pi.getTerm().getConvertibleLocation());
		Assertions.assertFalse(pi.getTerm().getReservation());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupNoMatch() {
		Assertions
				.assertNull(qiResource.lookup(subscription, builder().cpu(999).os(VmOs.SUSE).ephemeral(true).build()));
	}

	/**
	 * No such usage name.
	 */
	@Test
	void lookupUsageNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.lookup(subscription, builder().os(VmOs.LINUX).usage("any").build()));
	}

	@Test
	void lookupTypeNotFound() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.lookup(subscription, builder().type("any").build()));
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupOnlyCustom() {
		final var lookup = qiResource.lookup(subscription, builder().cpu(999).build());

		// Check the custom instance
		final var pi = lookup.getPrice();
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
	void lookupCustomIsCheaper() {
		assertPrice(qiResource.lookup(subscription, builder().ram(16000).usage("Dev").build()), "C74", "dynamic",
				147.453, "on-demand1");
	}

	/**
	 * Low usage rate, cheaper than 1y
	 */
	@Test
	void lookupVariableDuration() {
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev").build()), "C9", "instance2",
				58.56, "on-demand2");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Full Time 11 month").build()),
				"C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage(FULL).build()), "C11", "instance2",
				102.48, "1y");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Full Time 13 month").build()),
				"C9", "instance2", 117.12, "on-demand2");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Full Time 23 month").build()),
				"C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Full Time 24 month").build()),
				"C11", "instance2", 102.48, "1y");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Full Time 25 month").build()),
				"C9", "instance2", 117.12, "on-demand2");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev 11 month").build()), "C9",
				"instance2", 29.28, "on-demand2");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev 12 month").build()), "C9",
				"instance2", 29.28, "on-demand2");
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev 13 month").build()), "C9",
				"instance2", 29.28, "on-demand2");

		var usage = usageRepository.findByName("Dev 11 month");
		usage.setRate(90);
		usageRepository.saveAndFlush(usage);
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev 11 month").build()), "C9",
				"instance2", 105.408, "on-demand2");

		usage.setRate(98);
		usageRepository.saveAndFlush(usage);
		assertPrice(qiResource.lookup(subscription, builder().ephemeral(true).usage("Dev 11 month").build()), "C11",
				"instance2", 102.48, "1y");

	}

	private void assertPrice(final QuoteInstanceLookup lookup, final String code, final String instance,
			final double cost, final String term) {
		// Check the custom instance
		final var pi = lookup.getPrice();
		Assertions.assertEquals(code, pi.getCode());
		Assertions.assertEquals(instance, pi.getType().getName());
		Assertions.assertEquals(term, pi.getTerm().getName());
		Assertions.assertEquals(cost, lookup.getCost(), DELTA);

	}

	@Test
	void updateInstanceNonVisibleInstance() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findBy("type.name", "instanceX").getId());
		vo.setName("server1-bis");
		vo.setRam(1);
		vo.setCpu(0.5);
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.update(vo));
	}

	@Test
	void deleteAllInstances() {
		final var id = qiRepository.findByNameExpected("server1").getId();
		final var storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final var storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final var storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final var storageOther = qsRepository.findByNameExpected("shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storage2));
		Assertions.assertTrue(qsRepository.existsById(storage3));
		Assertions.assertEquals(8, qiRepository.count());
		Assertions.assertEquals(7, qiRepository.findAll(subscription).size());
		Assertions.assertEquals(4, qsRepository.findAll(subscription).size());
		em.flush();
		em.clear();

		// After delete, it remains only the unattached storages
		final var deleteAll = qiResource.deleteAll(subscription);
		checkCost(deleteAll, 2.73, 2.73, false);

		// Check deleted resources response
		final var deletedI = deleteAll.getDeleted().get(ResourceType.INSTANCE);
		final var deletedS = deleteAll.getDeleted().get(ResourceType.STORAGE);
		Assertions.assertEquals(7, deletedI.size());
		Assertions.assertTrue(deletedI.contains(id));
		Assertions.assertEquals(3, deletedS.size());
		Assertions.assertTrue(deletedS.contains(storage1));

		// The remaining storage for this subscription is not related to a delete instance
		Assertions.assertNull(qsRepository.findAll(subscription).get(0).getQuoteInstance());

		// Check the exact new cost
		checkCost(subscription, 2.73, 2.73, false);
		Assertions.assertNull(qiRepository.findOne(id));
		Assertions.assertEquals(0, qiRepository.findAll(subscription).size());

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertFalse(qsRepository.existsById(storage2));
		Assertions.assertFalse(qsRepository.existsById(storage3));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	void deleteAllInstancesWithSupport() throws IOException {
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class, ProvQuoteSupport.class },
				StandardCharsets.UTF_8.name());
		qsRepository.deleteAllBy("name", "shared-data");
		resource.refresh(subscription);
		checkCost(subscription, 3500.937, 6114.884, false);
		em.flush();
		em.clear();

		// There is only support
		checkCost(qiResource.deleteAll(subscription), 15, 15, false);
		checkCost(resource.getConfiguration(subscription).getCostNoSupport(), 0, 0, false);
		checkCost(resource.getConfiguration(subscription).getCostSupport(), 15, 15, false);
		checkCost(subscription, 15, 15, false);
		Assertions.assertEquals(0, qiRepository.findAll(subscription).size());
	}

	@Test
	void deleteInstance() {
		final var id = qiRepository.findByNameExpected("server1").getId();
		final var storage1 = qsRepository.findByNameExpected("server1-root").getId();
		final var storage2 = qsRepository.findByNameExpected("server1-data").getId();
		final var storage3 = qsRepository.findByNameExpected("server1-temp").getId();
		final var storageOther = qsRepository.findByNameExpected("shared-data").getId();
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
	void deleteUnboundInstance() {
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());

		final var vo = new QuoteInstanceEditionVo();
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
	void updateInstanceIdentity() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		final var updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotal(), 4704.758, 7154.358, false);
		checkCost(updatedCost.getCost(), 292.8, 1464, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		// Check the cost is the same
		updateCost();
	}

	@Test
	void updateInstanceUnbound() {
		var qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertFalse(qs.isUnboundCost());
		Assertions.assertEquals(8.4, qs.getCost(), DELTA);
		Assertions.assertEquals(42, qs.getMaxCost(), DELTA);

		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(null);
		var updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 4398.558, 4398.558, true);
		checkCost(updatedCost.getCost(), 146.4, 146.4, true);
		checkCost(subscription, 4398.558, 4398.558, true);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());
		qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertEquals(4.2, updatedCost.getRelated().get(ResourceType.STORAGE).get(qs.getId()).getMin(),
				DELTA);
		Assertions.assertEquals(4.2, updatedCost.getRelated().get(ResourceType.STORAGE).get(qs.getId()).getMax(),
				DELTA);
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).get(qs.getId()).isUnbound());
		Assertions.assertTrue(qs.isUnboundCost());
		Assertions.assertEquals(4.2, qs.getCost(), DELTA);
		Assertions.assertEquals(4.2, qs.getMaxCost(), DELTA);

		// Check the cost is the same
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		qiResource.update(vo);
		updateCost();
	}

	@Test
	void updateInstanceIncompatibleOs() {
		final var vo = new QuoteInstanceEditionVo();
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
	void updateInstance() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
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
		newTags(vo);

		final var updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 4460.778, 11460.758, false);
		checkCost(updatedCost.getCost(), 208.62, 4172.4, false);
		checkCost(subscription, 4460.778, 11460.758, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		final var instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("server1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(208.62, instance.getCost(), DELTA);
		Assertions.assertEquals(4172.4, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());
		assertTags(instance);

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		vo.setTags(null);
		final var updatedCost2 = qiResource.update(vo);
		checkCost(updatedCost2.getTotal(), 4356.468, 9374.558, false);
		checkCost(updatedCost2.getCost(), 104.31, 2086.2, false);

		// Change the region of this instance, storage is also
		vo.setLocation("region-2");

		// Check tags have not been updated
		assertTags(instance);
	}

	@Test
	void updateInstanceLocationNoMatchStorage() {
		// Add a storage only available in "region-1"
		final var qs = new ProvQuoteStorage();
		qs.setPrice(spRepository.findBy("type.name", "storage4"));
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
		final var vo = new QuoteInstanceEditionVo();
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
		checkCost(qiResource.update(vo).getTotal(), 3469.4, 7135.0, false);

		// "C1" -> "C7"
		checkCost(resource.refresh(subscription), 3447.44, 7025.2, false);
		vo.setPrice(ipRepository.findByExpected("code", "C7").getId());

		// No change
		checkCost(qiResource.update(vo).getTotal(), 3447.44, 7025.2, false);
		checkCost(resource.refresh(subscription), 3447.44, 7025.2, false);

		// Check the update failed because of "storage4"
		vo.setLocation("region-2"); // "region-1" to "region-2"
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.update(vo)),
				"storage", "no-match-storage");
	}

	@Test
	void updateInstanceLocation() {
		logQuote();

		// Engage first optimization
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		logQuote();

		// Everything identity but the region
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);

		// Check the exact new cost, same as initial
		checkCost(qiResource.update(vo).getTotal(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C7"
		checkCost(resource.refresh(subscription), 3143.44, 5505.2, false);
		logQuote();

		vo.setLocation("region-2"); // "region-1" -> "region-2"
		// Storage "server1-data" price changed for "region-2"
		// Storage "server1-temp" price changed for "region-2"
		checkCost(qiResource.update(vo).getTotal(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C7"
		checkCost(resource.refresh(subscription), 3164.32, 5609.6, false);
		logQuote();
	}

	private void logQuote() {
		final var vo3 = resource.getConfiguration(subscription);
		vo3.getInstances().forEach(q -> log.info(q.getName() + " -cost " + q.getCost() + " -type "
				+ q.getPrice().getType().getName() + " -code " + q.getPrice().getCode()));
		vo3.getStorages().forEach(
				q -> log.info(q.getName() + " -cost " + q.getCost() + " - type " + q.getPrice().getType().getName()));
	}

	@Test
	void updateInstanceOsCompatible() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server2").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C9").getId());
		vo.setName("server2-bis");
		vo.setOs(VmOs.CENTOS);
		vo.setRam(1024);
		vo.setCpu(0.5);
		qiResource.update(vo);
		final var instance = qiRepository.findOneExpected(vo.getId());
		Assertions.assertEquals(VmOs.CENTOS, instance.getOs());
	}

	@Test
	void createInstance() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setRamMax(10800);
		vo.setCpu(0.5);
		vo.setCpuMax(0.5);
		vo.setConstant(true);
		vo.setPhysical(false);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setMaxVariableCost(210.9);
		vo.setEphemeral(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		newTags(vo);
		final var updatedCost = qiResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 6790.958, 10283.658, false);
		checkCost(updatedCost.getCost(), 2086.2, 3129.3, false);
		Assertions.assertEquals(1, updatedCost.getRelated().size());
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).isEmpty());
		checkCost(subscription, 6790.958, 10283.658, false);
		final var instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertTrue(instance.isEphemeral());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals(1024, instance.getRam());
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
		assertTags(instance);
	}

	@Test
	void createInstanceIncompatibleOs() {
		final var vo = new QuoteInstanceEditionVo();
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
	void createUnboundInstance() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(null);
		final var updatedCost = qiResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 6790.958, 9240.558, true);
		checkCost(updatedCost.getCost(), 2086.2, 2086.2, true);
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).isEmpty());
		checkCost(subscription, 6790.958, 9240.558, true);
		final var instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertNull(instance.getMaxVariableCost());
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertNull(instance.getMaxQuantity());
		Assertions.assertTrue(instance.isUnboundCost());
	}

	@Test
	void createProcessor() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C61").getId());
		vo.setName("serverZ");
		vo.setOs(VmOs.LINUX);
		vo.setProcessor("Intel");
		final var updatedCost = qiResource.create(vo);

		checkCost(updatedCost.getCost(), 4684.8, 4684.8, true);
		final var instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("Intel", instance.getProcessor());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", instance.getPrice().getType().getProcessor());
	}

	@Test
	void createInstanceMinGreaterThanMax() {
		final var vo = new QuoteInstanceEditionVo();
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
	void findInstanceTerms() {
		final var tableItem = qiResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsCriteria() {
		final var tableItem = qiResource.findPriceTerms(subscription, newUriInfo("deMand"));
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qiResource.findPriceTerms(-1, newUriInfo()));
	}

	@Test
	void findInstancePriceTermsAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findPriceTerms(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findPriceTerms(subscription, newUriInfo()));
	}

	@Test
	void findLicenses() {
		final var tableItem = qiResource.findLicenses(subscription, VmOs.WINDOWS);
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("INCLUDED", tableItem.get(0));
		Assertions.assertEquals("BYOL", tableItem.get(1));
	}

	@Test
	void findLicensesNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findLicenses(subscription, VmOs.WINDOWS));
	}

	@Test
	void findSoftwares() {
		final var tableItem = qiResource.findSoftwares(subscription, VmOs.WINDOWS);
		Assertions.assertEquals(1, tableItem.size());
		Assertions.assertEquals("SQL Web", tableItem.get(0));
	}

	@Test
	void findSoftwaresNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findSoftwares(subscription, VmOs.WINDOWS));
	}

	@Test
	void findInstance() {
		final var tableItem = qiResource.findAllTypes(subscription, newUriInfo());
		Assertions.assertEquals(13, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceCriteria() {
		final var tableItem = qiResource.findAllTypes(subscription, newUriInfo("sTance1"));
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("instance1", tableItem.getData().get(0).getName());
		Assertions.assertEquals("instance10", tableItem.getData().get(1).getName());
	}

	@Test
	void findInstanceNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> qiResource.findAllTypes(-1, newUriInfo()));
	}

	@Test
	void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> qiResource.findAllTypes(subscription, newUriInfo()));
	}
}
