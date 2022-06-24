/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.container;

import static org.ligoj.app.plugin.prov.quote.container.QuoteContainerQuery.builder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.ProvBudgetResource;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvOptimizer;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteContainerResource}
 */
class ProvQuoteContainerResourceTest extends AbstractProvResourceTest {

	@Autowired
	protected ProvBudgetResource budgetResource;

	@Override
	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class,ProvOptimizer.class,ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/container", new Class[] { ProvContainerType.class, ProvContainerPrice.class,
				ProvQuoteContainer.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		preparePostData();
	}

	@Test
	void getConfigurationTest() {
		final var containers = resource.getConfiguration(subscription).getContainers();
		Assertions.assertEquals(7, containers.size());
	}

	@Test
	void refresh() {
		final var refresh = resource.refresh(subscription);
		checkCost(refresh, 3885.62, 5259.32, false);
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookup() {
		final var lookup = qcResource.lookup(subscription, builder().usage("Full Time 12 month").build());
		checkInstance(lookup);
	}
	
	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookupCo2() {
		final var lookup = qcResource.lookup(subscription, builder().usage("Full Time 12 month").optimizer("CO2").build());
		final var pi = lookup.getPrice();
		Assertions.assertEquals("LINUXD0", pi.getCode());
		Assertions.assertEquals(0.0, pi.getCost(), DELTA);
		Assertions.assertEquals(1100.0, lookup.getCo2(), DELTA);
		Assertions.assertEquals(1100.0, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price.
	 */
	@Test
	void lookupDynamical() {
		final var lookup = qcResource.lookup(subscription,
				builder().usage("Full Time 12 month").os(VmOs.LINUX).cpu(8).ram(2048).build());
		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("containerD0", pi.getType().getName());
		Assertions.assertEquals(0, pi.getType().getCpu());
		Assertions.assertEquals(0, pi.getType().getRam());
		Assertions.assertEquals(1, pi.getIncrementCpu());
		Assertions.assertEquals(1, pi.getMinCpu());
		Assertions.assertEquals("LINUXD0", pi.getCode());
		Assertions.assertEquals(0d, pi.getCost(), DELTA);
		Assertions.assertEquals(0d, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(8200.0, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price but deleted.
	 */
	@Test
	void lookupNoMatchDynamical() {
		cpRepository.deleteAllBy("code", "LINUXD0");
		Assertions.assertNull(qcResource.lookup(subscription, builder().cpu(100).build()));
	}

	/**
	 * Remove the dynamic type from the catalog.
	 */
	@Test
	void lookupNoDynamical() {
		ctRepository.deleteAllBy("name", "");
		lookup();
	}

	/**
	 * Builder coverage
	 */
	@Test
	void queryJson() throws IOException {
		new ObjectMapperTrim()
				.readValue("{\"os\":\"LINUX\"," + "\"cpu\":2,\"ram\":3000,\"constant\":true,\"license\":\"LI\""
						+ ",\"location\":\"L\",\"usage\":\"U\",\"type\":\"T\"}", QuoteContainerQuery.class);
		builder().toString();
	}

	private void checkInstance(final QuoteContainerLookup lookup) {
		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("container2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu());
		Assertions.assertEquals(2000, pi.getType().getRam());
		Assertions.assertEquals("LINUX3", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(168.0, pi.getCost(), DELTA);
		Assertions.assertEquals(2034.0, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals(VmOs.LINUX, pi.getOs());
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(168.0, lookup.getCost(), DELTA);
		Assertions.assertTrue(pi.toString().contains("os=LINUX"));
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupNoMatch() {
		Assertions.assertNull(qcResource.lookup(subscription, builder().cpu(999).build()));
	}

	@Test
	void deleteAll() {
		final var id = qcRepository.findByNameExpected("container1").getId();
		final var storage1 = qsRepository.findByNameExpected("container1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("container1-shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(8, qcRepository.count());
		em.flush();
		em.clear();

		// After delete, it remains only the unattached storages and non container instances
		checkCost(qcResource.deleteAll(subscription), 4385.158, 5556.358, false);

		// Check the exact new cost
		checkCost(subscription, 4385.158, 5556.358, false);
		Assertions.assertNull(qcRepository.findOne(id));
		Assertions.assertEquals(0, qcRepository.findAll(getQuote()).size());

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	void deleteAllWithSupport() throws IOException {
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class, ProvQuoteSupport.class },
				StandardCharsets.UTF_8.name());
		qsRepository.deleteAllBy("name", "container1-shared-data");
		resource.refresh(subscription);
		checkCost(subscription, 4293.179, 5741.42, false);
		em.flush();
		em.clear();

		// There is only quote instance with support
		checkCost(qcResource.deleteAll(subscription), 3149.377, 4436.984, false);
		checkCost(resource.getConfiguration(subscription).getCostNoSupport(), 2843.07, 4014.27, false);
		checkCost(resource.getConfiguration(subscription).getCostSupport(), 306.307, 422.714, false);
		checkCost(subscription, 3149.377, 4436.984, false);
		Assertions.assertEquals(0, qcRepository.findAll(getQuote()).size());
	}

	@Test
	void delete() {
		final var id = qcRepository.findByNameExpected("container1").getId();
		final var storage1 = qsRepository.findByNameExpected("container1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("container1-shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertFalse(repository.findBy("subscription.id", subscription).isUnboundCost());

		em.flush();
		em.clear();

		checkCost(qcResource.delete(id), 5104.178, 6275.378, false);

		// Check the exact new cost
		checkCost(subscription, 5104.178, 6275.378, false);
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertNull(qcRepository.findOne(id));

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	private Map<Integer, Floating> toStoragesFloating(final String instanceName) {
		return qsRepository.findAllBy("quoteContainer.name", instanceName).stream()
				.collect(Collectors.toMap(ProvQuoteStorage::getId, qs -> new Floating(qs.getCost(), qs.getMaxCost(), 0,
						0, qs.getQuoteContainer().getMaxQuantity() == null, qs.getCo2(), qs.getMaxCo2())));
	}

	@Test
	void updateIdentity() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloating("container1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteContainerEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qcRepository.findByNameExpected("container1").getId());
		vo.setPrice(cpRepository.findByExpected("code", "LINUX1").getId());
		vo.setName("container1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setGpu(0D);
		vo.setOs(VmOs.LINUX);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(2);
		final var updatedCost = qcResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotal(), 5332.478, 6731.978, false);
		checkCost(updatedCost.getCost(), 116.3, 232.6, false);

		// Check the related storage prices: only one attached container storage
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		// Check the cost is the same
		updateCost();
	}

	@Test
	void update() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloating("container1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteContainerEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qcRepository.findByNameExpected("container1").getId());
		vo.setPrice(cpRepository.findByExpected("code", "LINUX1").getId());
		vo.setName("container1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setGpu(0D);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		vo.setLocation("region-1");
		vo.setUsage("Full Time");
		vo.setOs(VmOs.LINUX);
		final var updatedCost = qcResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 5332.478, 10841.378, false);
		checkCost(updatedCost.getCost(), 116.3, 2326.0, false);
		checkCost(subscription, 5332.478, 10841.378, false);

		// Check the related storage prices: only one attached storage
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		final var instance = qcRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("container1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(0, instance.getGpu(), DELTA);
		Assertions.assertEquals(116.3, instance.getCost(), DELTA);
		Assertions.assertEquals(2326.0, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		final var updatedCost2 = qcResource.update(vo);
		checkCost(updatedCost2.getTotal(), 5274.328, 9678.378, false);
		checkCost(updatedCost2.getCost(), 58.15, 1163.0, false);

		// Change the region of this instance, storage is also
		vo.setLocation("region-2");
	}

	@Test
	void create() {
		final var vo = new QuoteContainerEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(cpRepository.findByExpected("code", "WINDOWS1").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setGpu(0D);
		vo.setConstant(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		vo.setOs(VmOs.WINDOWS);
		final var updatedCost = qcResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 6796.478, 8927.978, false);
		checkCost(updatedCost.getCost(), 1464.0, 2196.0, false);
		Assertions.assertEquals(1, updatedCost.getRelated().size());
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).isEmpty());
		checkCost(subscription, 6796.478, 8927.978, false);
		final var instance = qcRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(0, instance.getGpu(), DELTA);
		Assertions.assertEquals(VmOs.WINDOWS, instance.getOs());
		Assertions.assertEquals(1464.0, instance.getCost(), DELTA);
		Assertions.assertEquals(2196.0, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());
	}

	@Test
	void createProcessor() {
		final var vo = new QuoteContainerEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(cpRepository.findByExpected("code", "LINUX3").getId());
		vo.setName("serverZ");
		vo.setOs(VmOs.LINUX);
		vo.setProcessor("Intel");
		final var updatedCost = qcResource.create(vo);

		checkCost(updatedCost.getCost(), 168.0, 168.0, true);
		final var instance = qcRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals(VmOs.LINUX, instance.getOs());
		Assertions.assertEquals("Intel", instance.getProcessor());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", instance.getPrice().getType().getProcessor());
	}

	@Test
	void findInstanceTerms() {
		final var tableItem = qcResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qcResource.findPriceTerms(-1, uri));
	}

	@Test
	void findInstancePriceTermsAnotherSubscription() {
		final var uri = newUriInfo();
		Assertions.assertEquals(1,
				qcResource.findPriceTerms(getSubscription("mda", "service:prov:x"), uri).getData().size());
	}

	@Test
	void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qcResource.findPriceTerms(subscription, uri));
	}

	@Test
	void findLicenses() {
		final var tableItem = qcResource.findLicenses(subscription, VmOs.WINDOWS);
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("INCLUDED", tableItem.get(0));
		Assertions.assertEquals("BYOL", tableItem.get(1));
	}

	@Test
	void findLicensesNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qcResource.findLicenses(subscription, VmOs.LINUX));
	}

	@Test
	void findAllTypes() {
		final var tableItem = qcResource.findAllTypes(subscription, newUriInfo());
		Assertions.assertEquals(4, tableItem.getRecordsTotal());
		Assertions.assertEquals("container1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceCriteria() {
		final var tableItem = qcResource.findAllTypes(subscription, newUriInfo("tainer1"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("container1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qcResource.findAllTypes(-1, uri));
	}

	@Test
	void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qcResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qcResource.findAllTypes(subscription, uri));
	}

	@Override
	protected Floating updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(5332.478, cost.getMin(), DELTA);
		Assertions.assertEquals(6731.978, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 5332.478, 6731.978, false);
		em.flush();
		em.clear();
		return cost;
	}

	@Test
	void getSubscriptionStatus() {
		final var status = resource.getSubscriptionStatus(subscription);
		Assertions.assertEquals("quote1", status.getName());
		Assertions.assertEquals("quoteD1", status.getDescription());
		Assertions.assertNotNull(status.getId());
		checkCost(status.getCost(), 5332.478, 6731.978, false);
		Assertions.assertEquals(7, status.getNbInstances());
		Assertions.assertEquals(7, status.getNbContainers());
		Assertions.assertEquals(14, status.getTotalCpu(), 0.0001); // 10.75 + 3,25 (Container)
		Assertions.assertEquals(57976, status.getTotalRam());
		Assertions.assertEquals(13, status.getNbPublicAccess());
		Assertions.assertEquals(4, status.getNbStorages());
		Assertions.assertEquals(104, status.getTotalStorage());
		Assertions.assertEquals("region-1", status.getLocation().getName());
	}

	@Test
	void findCointainerOs() {
		final var tableItem = qcResource.findOs(subscription);
		Assertions.assertEquals(2, tableItem.size());
		Assertions.assertEquals("LINUX", tableItem.get(0));
	}

	@Test
	void findContainerOsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qcResource.findOs(subscription));
	}
}
