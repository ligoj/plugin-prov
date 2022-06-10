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
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
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
class ProvQuoteInstanceResourceTest extends AbstractProvResourceTest {

	private static final String FULL = "Full Time 12 month";

	@Autowired
	private ProvQuoteRepository repository;

	/**
	 * Builder coverage
	 */
	@Test
	void queryJson() throws IOException {
		new ObjectMapperTrim().readValue("{\"software\":\"S\",\"ephemeral\":true,"
				+ "\"cpu\":2,\"gpu\":3,\"ram\":3000,\"constant\":true,\"license\":\"LI\",\"os\":\"LINUX\","
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
	 * Rate based lookup.
	 */
	@Test
	void lookupRate() {
		var build = builder().cpuRate(Rate.BEST).build();
		Assertions.assertEquals("instance2",
				qiResource.lookup(subscription, builder().cpuRate(Rate.BEST).build()).getPrice().getType().getCode());
		Assertions.assertEquals("instance2",
				qiResource.lookup(subscription, builder().gpuRate(Rate.BEST).build()).getPrice().getType().getCode());
		Assertions.assertEquals("instance2",
				qiResource.lookup(subscription, builder().ramRate(Rate.GOOD).build()).getPrice().getType().getCode());
		build = builder().storageRate(Rate.BEST).networkRate(Rate.BEST).ramRate(Rate.BEST).cpuRate(Rate.BEST)
				.gpuRate(Rate.BEST).build();
		build.setCpuRate(Rate.BEST); // Coverage only
		build.setGpuRate(Rate.BEST); // Coverage only
		build.setRamRate(Rate.BEST); // Coverage only
		build.setNetworkRate(Rate.BEST); // Coverage only
		build.setStorageRate(Rate.BEST); // Coverage only
		Assertions.assertNull(qiResource.lookup(subscription, build));
	}

	/**
	 * AutoScale based lookup.
	 */
	@Test
	void lookupAutoScale() {
		var build = builder().autoScale(true).build();
		build.setAutoScale(true); // Coverage only
		Assertions.assertEquals("instance2", qiResource.lookup(subscription, build).getPrice().getType().getCode());
		Assertions.assertNull(qiResource.lookup(subscription, builder().autoScale(true).cpu(2).build()));

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
		final var build = builder().ramMax(2000).cpuMax(2d).gpuMax(0d).build();
		build.setCpuMax(2d); // Only for coverage
		build.setGpuMax(0d); // Only for coverage
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
		Assertions.assertEquals("instance5",
				qiResource.lookup(subscription, builder().ram(2000).usage(FULL).location("region-2").build()).getPrice()
						.getType().getName());
	}

	/**
	 * Search instance type within a non existing region
	 */
	@Test
	void lookupLocationNotFound() {
		final var vo = builder().location("region-xxx").build();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.lookup(subscription, vo));
	}

	private void checkInstance(final QuoteInstanceLookup lookup) {
		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu());
		Assertions.assertEquals(2000, pi.getType().getRam());
		Assertions.assertEquals("C11", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(102.48, pi.getCost(), DELTA);
		Assertions.assertEquals(1229.76, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("1y", pi.getTerm().getName());
		Assertions.assertEquals(102.48, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup constant and low RAM.
	 */
	@Test
	void lookupHighConstraints() throws IOException {
		final var build = builder().cpu(3).ram(9).constant(true).tenancy(ProvTenancy.SHARED).os(VmOs.WINDOWS)
				.usage(FULL).build();
		build.setTenancy(ProvTenancy.SHARED); // Coverage only
		final var lookupObj = qiResource.lookup(subscription, build);
		final var lookup = new ObjectMapperTrim().readValue(new ObjectMapperTrim().writeValueAsString(lookupObj),
				QuoteInstanceLookup.class);
		final var pi = lookup.getPrice();
		Assertions.assertEquals("C54", pi.getCode());
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance9", pi.getType().getName());
		Assertions.assertEquals(4, pi.getType().getCpu());
		Assertions.assertEquals(16000, pi.getType().getRam());
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
	 * Advanced case, term's location constraints.
	 */
	@Test
	void lookupConvertibleKo() {
		Assertions.assertNull(qiResource.lookup(subscription,
				builder().cpu(1).usage("Full Time Global").location("region-1").build()));
	}

	/**
	 * Convertible location
	 */
	@Test
	void lookupConvertibleLocation() {
		final var lookup = qiResource.lookup(subscription,
				builder().cpu(1).usage("Full Time Global").location("region-5").build());
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
	 * Convertible OS
	 */
	@Test
	void lookupConvertibleOs() {
		final var lookup = qiResource.lookup(subscription,
				builder().cpu(1).os(VmOs.LINUX).usage("Full Time Convertible").build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(102.48, pi.getCost(), DELTA);
		Assertions.assertEquals("C11", pi.getCode());
		Assertions.assertEquals("1y", pi.getTerm().getName());
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	void lookupConvertibleLocationOnGlobal() {
		final var lookup = qiResource.lookup(subscription,
				builder().cpu(1).usage("Full Time 12 month").location("region-1").build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(102.48, pi.getCost(), DELTA);
		Assertions.assertEquals("1y", pi.getTerm().getName());
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
		final var vo = builder().os(VmOs.LINUX).usage("any").build();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.lookup(subscription, vo));
	}

	/**
	 * No such budget name.
	 */
	@Test
	void lookupBudgetNotFound() {
		final var vo = builder().os(VmOs.LINUX).budget("any").build();
		vo.setBudget("any"); // Coverage only
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.lookup(subscription, vo));
	}

	@Test
	void lookupTypeNotFound() {
		final var vo = builder().type("any").build();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.lookup(subscription, vo));
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
		Assertions.assertEquals(0, pi.getType().getCpu());
		Assertions.assertEquals(0, pi.getType().getGpu());
		Assertions.assertEquals(0, pi.getType().getRam());
		Assertions.assertTrue(pi.getType().getConstant());
		Assertions.assertEquals(0, pi.getCost(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertTrue(pi.getType().isCustom());

		Assertions.assertEquals(242594.101, lookup.getCost(), DELTA);
	}

	/**
	 * This configuration suits to a custom instance.
	 */
	@Test
	void lookupCustomIsCheaper() {
		assertPrice(qiResource.lookup(subscription, builder().ram(15360).usage("Dev").build()), "C74", "dynamic",
				145.825d, "on-demand1");
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

	@Test
	void lookupGpu() {
		final var lookup = qiResource.lookup(subscription, builder().gpu(2).build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals(2.0, pi.getType().getGpu());
		Assertions.assertEquals("instance3", pi.getType().getName());
		Assertions.assertEquals(2, pi.getType().getCpu());
		Assertions.assertEquals(2000, pi.getType().getRam());
		Assertions.assertEquals("C13", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(292.8, pi.getCost(), DELTA);
		Assertions.assertEquals(292.8, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(292.8, lookup.getCost(), DELTA);

	}

	@Test
	void lookupGpu0() {
		final var lookup = qiResource.lookup(subscription, builder().gpu(0.0).build());
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("instance2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu());
		Assertions.assertEquals(0.0, pi.getType().getGpu());
		Assertions.assertEquals(2000, pi.getType().getRam());
		Assertions.assertEquals("C7", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(135.42, pi.getCost(), DELTA);
		Assertions.assertEquals(135.42, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(135.42, lookup.getCost(), DELTA);
		;
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
		vo.setGpu(0D);
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
		Assertions.assertEquals(7, qiRepository.findAll(getQuote()).size());
		Assertions.assertEquals(4, qsRepository.findAll(getQuote()).size());
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
		Assertions.assertNull(qsRepository.findAll(getQuote()).get(0).getQuoteInstance());

		// Check the exact new cost
		checkCost(subscription, 2.73, 2.73, false);
		Assertions.assertNull(qiRepository.findOne(id));
		Assertions.assertEquals(0, qiRepository.findAll(getQuote()).size());

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
		Assertions.assertEquals(0, qiRepository.findAll(getQuote()).size());
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
		vo.setGpu(0D);
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

	private Map<Integer, Floating> toStoragesFloating(final String instanceName) {
		return qsRepository.findAllBy("quoteInstance.name", instanceName).stream()
				.collect(Collectors.toMap(ProvQuoteStorage::getId, qs -> new Floating(qs.getCost(), qs.getMaxCost(), 0,
						0, qs.getQuoteInstance().getMaxQuantity() == null, qs.getCo2(), qs.getMaxCo2())));
	}

	@Test
	void updateInstanceIdentity() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloating("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setGpu(0D);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);
		var updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotal(), 4704.758, 7154.358, false);
		checkCost(updatedCost.getCost(), 292.8, 1464, false);

		// Check the related storage prices
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		// Check the cost is the same
		updateCost();

		// Identity update but with enabled lean
		getQuote().setLeanOnChange(true);
		em.flush();
		em.clear();
		updatedCost = qiResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());
		checkCost(updatedCost.getTotal(), 3143.44, 5505.2, false);
	}

	@Test
	void updateInstanceUnbound() {
		var qs = qsRepository.findByNameExpected("server1-root");
		Assertions.assertFalse(qs.isUnboundCost());
		Assertions.assertEquals(8.4, qs.getCost(), DELTA);
		Assertions.assertEquals(42, qs.getMaxCost(), DELTA);

		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloating("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C1").getId());
		vo.setName("server1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setGpu(0D);
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
		vo.setGpu(0D);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.update(vo)),
				"os", "incompatible-os");
	}

	@Test
	void updateInstance() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloating("server1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qiRepository.findByNameExpected("server1").getId());
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("server1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setGpu(0D);
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
		Assertions.assertEquals(0, instance.getGpu(), DELTA);
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
		vo.setGpu(0D);
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
		vo.setGpu(0D);
		vo.setMinQuantity(2);
		vo.setMaxQuantity(10);

		// Check the exact new cost, same as initial
		checkCost(qiResource.update(vo).getTotal(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C7"
		checkCost(resource.refresh(subscription), 3143.44, 5505.2, false);
		Assertions.assertEquals("C7", qiRepository.findByNameExpected("server1").getPrice().getCode());
		logQuote();

		vo.setLocation("region-2"); // "region-1" -> "region-2"
		// Storage "server1-data" price changed for "region-2"
		// Storage "server1-temp" price changed for "region-2"
		checkCost(qiResource.update(vo).getTotal(), 3165.4, 5615.0, false);
		logQuote();

		// Price "C1" is replaced by "C98"
		checkCost(resource.refresh(subscription), 6092.32, 20249.6, false);
		Assertions.assertEquals("C98", qiRepository.findByNameExpected("server1").getPrice().getCode());
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
		vo.setGpu(0D);
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
		vo.setGpu(0D);
		vo.setGpuMax(0D);
		vo.setConstant(true);
		vo.setPhysical(false);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setTenancy(ProvTenancy.SHARED);
		vo.setMaxVariableCost(210.9);
		vo.setEphemeral(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);

		vo.setAutoScale(true);

		vo.setRamRate(Rate.LOW);
		vo.setCpuRate(Rate.MEDIUM);
		vo.setGpuRate(Rate.MEDIUM);
		vo.setNetworkRate(Rate.WORST);
		vo.setStorageRate(Rate.MEDIUM);

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
		Assertions.assertEquals(0, instance.getGpu(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assertions.assertEquals(2086.2, instance.getCost(), DELTA);
		Assertions.assertEquals(3129.3, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assertions.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());

		Assertions.assertEquals(Rate.LOW, instance.getRamRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getCpuRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getGpuRate());
		Assertions.assertEquals(Rate.WORST, instance.getNetworkRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getStorageRate());

		assertTags(instance);
	}

	@Test
	void createInstanceGpu() {
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setRamMax(10800);
		vo.setCpu(0.5);
		vo.setCpuMax(0.5);
		vo.setGpu(1D);
		vo.setGpuMax(2D);
		vo.setConstant(true);
		vo.setPhysical(false);
		vo.setInternet(InternetAccess.PUBLIC);
		vo.setTenancy(ProvTenancy.SHARED);
		vo.setMaxVariableCost(210.9);
		vo.setEphemeral(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);

		vo.setAutoScale(true);

		vo.setRamRate(Rate.LOW);
		vo.setCpuRate(Rate.MEDIUM);
		vo.setGpuRate(Rate.MEDIUM);
		vo.setGpuRate(Rate.MEDIUM);
		vo.setNetworkRate(Rate.WORST);
		vo.setStorageRate(Rate.MEDIUM);

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
		Assertions.assertEquals(1, instance.getGpu(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assertions.assertEquals(2086.2, instance.getCost(), DELTA);
		Assertions.assertEquals(3129.3, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(InternetAccess.PUBLIC, instance.getInternet());
		Assertions.assertEquals(210.9, instance.getMaxVariableCost(), DELTA);
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());

		Assertions.assertEquals(Rate.LOW, instance.getRamRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getCpuRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getGpuRate());
		Assertions.assertEquals(Rate.WORST, instance.getNetworkRate());
		Assertions.assertEquals(Rate.MEDIUM, instance.getStorageRate());

		assertTags(instance);
	}

	@Test
	void createInstanceAutoLean() {
		// Pre-condition
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		Assertions.assertFalse(getQuote().getLeanOnChange());

		// Standard create
		final var vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(ipRepository.findByExpected("code", "C10").getId());
		vo.setName("serverZ");
		vo.setRam(1024);
		vo.setMaxQuantity(1);
		vo.setRamMax(10800);
		vo.setCpu(0.5);
		vo.setGpu(0D);
		vo.setOs(VmOs.WINDOWS);
		vo.setCpuMax(0.5);
		vo.setGpuMax(0d);
		vo.setUsage(FULL); // Important for the price select -> C12
		vo.setBudget("Dept2");
		var updatedCost = qiResource.create(vo);

		// Check the exact new cost with no-lean on change
		checkCost(updatedCost.getTotal(), 3374.02, 5823.62, false);
		var instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("Dept2", instance.getBudgetName());
		Assertions.assertEquals("Dept2", instance.getBudget().getName());
		Assertions.assertEquals("C10", instance.getPrice().getCode());
		em.flush();
		em.clear();

		// Apply lean, correspond to the amount with auto lean
		checkCost(resource.refresh(subscription), 3403.3, 5852.9, false);
		instance = qiRepository.findOneExpected(updatedCost.getId());

		// C10->C8 (lack of budget)
		Assertions.assertEquals("C8", instance.getPrice().getCode());

		// Apply lean on change mode
		getQuote().setLeanOnChange(true);

		// Rollback to previous state, and with lean auto
		qiResource.delete(updatedCost.getId());

		// New price is already with lean
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		Assertions.assertTrue(getQuote().getLeanOnChange());
		em.flush();
		em.clear();

		// Add again the resource, expecting the lean is applied immediately
		updatedCost = qiResource.create(vo);
		checkCost(resource.refresh(subscription), 3403.3, 5852.9, false);
		instance = qiRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("C8", instance.getPrice().getCode());
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
		vo.setGpu(0D);
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
		vo.setGpu(0D);
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
		vo.setGpu(0D);
		vo.setMinQuantity(100);
		vo.setMaxQuantity(10);
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qiResource.create(vo)),
				"maxQuantity", "Min");
	}

	@Test
	void findInstanceTerms() {
		final var tableItem = qiResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsCriteria() {
		final var tableItem = qiResource.findPriceTerms(subscription, newUriInfo("deMand"));
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qiResource.findPriceTerms(-1, uri));
	}

	@Test
	void findInstancePriceTermsAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findPriceTerms(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.findPriceTerms(subscription, uri));
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
		Assertions.assertEquals("SQL WEB", tableItem.get(0));
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
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qiResource.findAllTypes(-1, uri));
	}

	@Test
	void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qiResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.findAllTypes(subscription, uri));
	}

	@Test
	void findOs() {
		final var tableItem = qiResource.findOs(subscription);
		Assertions.assertEquals(3, tableItem.size());
		Assertions.assertEquals("LINUX", tableItem.get(0));
	}

	@Test
	void findOsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qiResource.findOs(subscription));
	}
}
