/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.function;

import static org.ligoj.app.plugin.prov.quote.function.QuoteFunctionQuery.builder;

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
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvBudgetResource;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteFunctionResource}
 */
class ProvQuoteFunctionResourceTest extends AbstractProvResourceTest {

	@Autowired
	protected ProvBudgetResource budgetResource;

	@Override
	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/function", new Class[] { ProvFunctionType.class, ProvFunctionPrice.class,
				ProvQuoteFunction.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		preparePostData();
	}

	@Test
	void getConfigurationTest() {
		final var entries = resource.getConfiguration(subscription).getFunctions();
		Assertions.assertEquals(2, entries.size());
	}

	@Test
	void refresh() {
		final var refresh = resource.refresh(subscription);
		checkCost(refresh, 3074.1, 4473.6, false);
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookup() {
		final var build = builder().runtime("Node").usage("Full Time 12 month").nbRequests(200).build();
		final var lookup = qfResource.lookup(subscription, build);
		Assertions.assertEquals("Node", build.getRuntime());
		checkFunction(lookup);
	}

	/**
	 * Lookup for a only dynamic price and an adjusted concurrency: success
	 */
	@Test
	void lookupDynamicalOptimizedConcurrencyOk() {
		// Check with optimized concurrency discovery: succeed, use 2
		var lookup = qfResource.lookup(subscription,
				builder().usage("Dev").nbRequests(20).duration(200).ram(2048).concurrency(1.9).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND1", pi.getCode());
		Assertions.assertEquals(43.8d, pi.getCostRamRequest());
		Assertions.assertEquals(25.55d, pi.getCostRamRequestConcurrency());
		Assertions.assertEquals(1, pi.getMinDuration());
		Assertions.assertEquals(1, pi.getIncrementDuration());
		Assertions.assertEquals(1d / 1024d, pi.getIncrementRam(), DELTA);
		Assertions.assertEquals(124.558, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price and an adjusted concurrency: keep the floor version
	 */
	@Test
	void lookupDynamicalOptimizedConcurrencyKo() {
		// Check with optimized concurrency discovery: failed, keep 1
		var lookup = qfResource.lookup(subscription,
				builder().usage("Dev").nbRequests(20).duration(200).ram(2048).concurrency(1.4).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND1", pi.getCode());
		Assertions.assertEquals(130.033, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price without concurrency
	 */
	@Test
	void lookupDynamicalNoConcurrency() {
		var lookup = qfResource.lookup(subscription,
				builder().usage("Full Time").nbRequests(20).duration(200).ram(2048).build());
		// Check the instance result
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND0", pi.getCode());
		Assertions.assertEquals(137.333, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price with concurrency.
	 */
	@Test
	void lookupDynamicalConcurrency() {
		// Check the instance with over provisioned concurrency
		var lookup = qfResource.lookup(subscription,
				builder().usage("Full Time").nbRequests(20).duration(200).ram(2048).concurrency(2).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND1", pi.getCode());
		Assertions.assertEquals(125.578, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price with concurrency covering the all requests.
	 */
	@Test
	void lookupDynamicalConcurrencyLow() {
		// Check the instance with lower provisioned concurrency
		var lookup = qfResource.lookup(subscription,
				builder().usage("Full Time").nbRequests(20).duration(200).ram(2048).concurrency(1).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND1", pi.getCode());
		Assertions.assertEquals(122.733, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price with concurrency but only at part-time.
	 */
	@Test
	void lookupDynamicalConcurrencyDev() {
		// Check the instance with partial up and lower provisioned concurrency
		var lookup = qfResource.lookup(subscription,
				builder().usage("Dev").nbRequests(20).duration(200).ram(2048).concurrency(1).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND1", pi.getCode());
		Assertions.assertEquals(130.033, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price requesting RAM at the edge of the increment.
	 */
	@Test
	void lookupDynamicalIncrement() {
		// Check the instance with partial resources
		var lookup = qfResource.lookup(subscription, builder().usage("Full Time").type("functiond2").nbRequests(20)
				.concurrency(1).duration(75 + 1).ram(2048 - 128 + 1).build());
		var pi = lookup.getPrice();
		Assertions.assertEquals("FUNCTIOND2", pi.getCode());
		Assertions.assertEquals(89.4, lookup.getCost(), DELTA);

		lookup = qfResource.lookup(subscription, builder().usage("Full Time").type("functiond2").nbRequests(20)
				.concurrency(1).duration(150).ram(2048).build());
		Assertions.assertEquals(89.4, lookup.getCost(), DELTA);

		lookup = qfResource.lookup(subscription, builder().usage("Full Time").type("functiond2").nbRequests(20)
				.concurrency(1).duration(151).ram(100).build());
		Assertions.assertEquals(12.462, lookup.getCost(), DELTA);

		lookup = qfResource.lookup(subscription, builder().usage("Full Time").type("functiond2").nbRequests(20)
				.concurrency(1).duration(150 + 75 - 1).ram(127).build());
		Assertions.assertEquals(12.462, lookup.getCost(), DELTA);
	}

	/**
	 * Lookup for a only dynamic price but deleted.
	 */
	@Test
	void lookupNoMatchDynamical() {
		fpRepository.deleteAllBy("code", "FUNCTIOND0");
		Assertions.assertNull(qfResource.lookup(subscription, builder().cpu(100).build()));
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
		new ObjectMapperTrim().readValue(
				"{\"cpu\":2,\"ram\":3000,\"nbRequests\":20,\"runtime\":\"Java\""
						+ ",\"location\":\"L\",\"usage\":\"U\",\"type\":\"T\",\"concurrency\":10,\"duration\":200}",
				QuoteFunctionQuery.class);
		builder().toString();
	}

	private void checkFunction(final QuoteFunctionLookup lookup) {
		// Check the instance result
		final var pi = lookup.getPrice();
		Assertions.assertNotNull(pi.getId());
		Assertions.assertEquals("function2", pi.getType().getName());
		Assertions.assertEquals(1, pi.getType().getCpu());
		Assertions.assertEquals(2000, pi.getType().getRam());
		Assertions.assertEquals("FUNCTION3", pi.getCode());
		Assertions.assertFalse(pi.getTerm().isEphemeral());
		Assertions.assertEquals(168.0, pi.getCost(), DELTA);
		Assertions.assertEquals(2034.0, pi.getCostPeriod(), DELTA);
		Assertions.assertEquals("on-demand1", pi.getTerm().getName());
		Assertions.assertEquals(168.0, lookup.getCost(), DELTA);
		Assertions.assertTrue(pi.toString().contains("name=on-demand1"), pi.toString());

		// Coverage only
		Assertions.assertTrue(lookup.toString().contains("name=on-demand1"));
		new ProvQuoteFunction().setStorages(null);
		Assertions.assertNotNull(qfResource.getItRepository());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	void lookupNoMatch() {
		Assertions.assertNull(qfResource.lookup(subscription, builder().cpu(999).build()));
	}

	@Test
	void deleteAll() {
		final var id = qfRepository.findByNameExpected("function1").getId();
		final var storage1 = qsRepository.findByNameExpected("function1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("function1-shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(3, qfRepository.count());
		em.flush();
		em.clear();

		// After delete, it remains only the unattached storages and non function instances
		checkCost(qfResource.deleteAll(subscription), 4385.158, 5556.358, false);

		// Check the exact new cost
		checkCost(subscription, 4385.158, 5556.358, false);
		Assertions.assertNull(qfRepository.findOne(id));
		Assertions.assertEquals(0, qfRepository.findAll(getQuote()).size());

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	@Test
	void deleteAllWithSupport() throws IOException {
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class, ProvQuoteSupport.class },
				StandardCharsets.UTF_8.name());
		qsRepository.deleteAllBy("name", "function1-shared-data");
		resource.refresh(subscription);
		checkCost(subscription, 3400.507, 4916.414, false);
		em.flush();
		em.clear();

		// There is only quote instance with support
		checkCost(qfResource.deleteAll(subscription), 3149.377, 4436.984, false);
		checkCost(resource.getConfiguration(subscription).getCostNoSupport(), 2843.07, 4014.27, false);
		checkCost(resource.getConfiguration(subscription).getCostSupport(), 306.307, 422.714, false);
		checkCost(subscription, 3149.377, 4436.984, false);
		Assertions.assertEquals(0, qfRepository.findAll(getQuote()).size());
	}

	@Test
	void delete() {
		final var id = qfRepository.findByNameExpected("function1").getId();
		final var storage1 = qsRepository.findByNameExpected("function1-root").getId();
		final var storageOther = qsRepository.findByNameExpected("function1-shared-data").getId();
		Assertions.assertTrue(qsRepository.existsById(storage1));
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertFalse(repository.findBy("subscription.id", subscription).isUnboundCost());

		em.flush();
		em.clear();

		checkCost(qfResource.delete(id), 4474.658, 5645.858, false);

		// Check the exact new cost
		checkCost(subscription, 4474.658, 5645.858, false);
		Assertions.assertEquals(0,
				repository.findBy("subscription.id", subscription).getUnboundCostCounter().intValue());
		Assertions.assertNull(qfRepository.findOne(id));

		// Also check the associated storage is deleted
		Assertions.assertFalse(qsRepository.existsById(storage1));
		Assertions.assertTrue(qsRepository.existsById(storageOther));
	}

	private Map<Integer, FloatingCost> toStoragesFloatingCost(final String instanceName) {
		return qsRepository.findAllBy("quoteFunction.name", instanceName).stream()
				.collect(Collectors.toMap(ProvQuoteStorage::getId, qs -> new FloatingCost(qs.getCost(), qs.getMaxCost(),
						0, 0, qs.getQuoteFunction().getMaxQuantity() == null)));
	}

	@Test
	void updateIdentity() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("function1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteFunctionEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qfRepository.findByNameExpected("function1").getId());
		vo.setPrice(fpRepository.findByExpected("code", "FUNCTION1").getId());
		vo.setName("function1-bis");
		vo.setRam(2000);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(2);
		vo.setNbRequests(10);
		vo.setRuntime("Node");
		vo.setConcurrency(2);
		vo.setDuration(200);
		final var updatedCost = qfResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost, same as initial
		checkCost(updatedCost.getTotal(), 4702.958, 6102.458, false);
		checkCost(updatedCost.getCost(), 116.3, 232.6, false);

		// Check the related storage prices: only one attached function storage
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		// Check the cost is the same
		updateCost();

		final var qi = qfRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("Node", qi.getRuntime());
		Assertions.assertEquals(2, qi.getConcurrency());
		Assertions.assertEquals(200, qi.getDuration());
		Assertions.assertEquals("FUNCTION1", qi.getPrice().getCode());
		Assertions.assertEquals(1.1d, qi.getPrice().getCostRequests());
	}

	@Test
	void update() {
		// Check the cost of related storages of this instance
		final var storagePrices = toStoragesFloatingCost("function1");
		Assertions.assertEquals(3, storagePrices.size());

		final var vo = new QuoteFunctionEditionVo();
		vo.setSubscription(subscription);
		vo.setId(qfRepository.findByNameExpected("function1").getId());
		vo.setPrice(fpRepository.findByExpected("code", "FUNCTION1").getId());
		vo.setName("function1-bis");
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setMinQuantity(1);
		vo.setMaxQuantity(20);
		vo.setLocation("region-1");
		vo.setUsage("Full Time");
		vo.setNbRequests(10);
		final var updatedCost = qfResource.update(vo);
		Assertions.assertEquals(updatedCost.getId(), vo.getId());

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 4702.958, 10211.858, false);
		checkCost(updatedCost.getCost(), 116.3, 2326.0, false);
		checkCost(subscription, 4702.958, 10211.858, false);

		// Check the related storage prices: only one attached storage
		Assertions.assertEquals(3, updatedCost.getRelated().get(ResourceType.STORAGE).size());

		final var instance = qfRepository.findOneExpected(vo.getId());
		Assertions.assertEquals("function1-bis", instance.getName());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(116.3, instance.getCost(), DELTA);
		Assertions.assertEquals(2326.0, instance.getMaxCost(), DELTA);
		Assertions.assertEquals("region-1", instance.getLocation().getName());

		// Change the usage of this instance to 50%
		vo.setUsage("Dev");
		final var updatedCost2 = qfResource.update(vo);
		checkCost(updatedCost2.getTotal(), 4644.808, 9048.858, false);
		checkCost(updatedCost2.getCost(), 58.15, 1163.0, false);

		// Change the region of this instance, storage is also
		vo.setLocation("region-2");
	}

	@Test
	void create() {
		final var vo = new QuoteFunctionEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(fpRepository.findByExpected("code", "FUNCTION1").getId());
		vo.setName("serverZ");
		vo.setDescription("serverZD");
		vo.setRuntime("Java");
		vo.setConcurrency(100);
		vo.setDuration(100);
		vo.setRam(1024);
		vo.setCpu(0.5);
		vo.setConstant(true);
		vo.setMinQuantity(10);
		vo.setMaxQuantity(15);
		vo.setNbRequests(10);
		final var updatedCost = qfResource.create(vo);

		// Check the exact new cost
		checkCost(updatedCost.getTotal(), 5865.958, 7846.958, false);
		checkCost(updatedCost.getCost(), 1163.0, 1744.5, false);
		Assertions.assertEquals(1, updatedCost.getRelated().size());
		Assertions.assertTrue(updatedCost.getRelated().get(ResourceType.STORAGE).isEmpty());
		checkCost(subscription, 5865.958, 7846.958, false);
		final var instance = qfRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals("serverZ", instance.getName());
		Assertions.assertEquals("serverZD", instance.getDescription());
		Assertions.assertEquals("Java", instance.getRuntime());
		Assertions.assertEquals(100, instance.getConcurrency());
		Assertions.assertEquals(100, instance.getDuration());
		Assertions.assertEquals(1024, instance.getRam());
		Assertions.assertEquals(0.5, instance.getCpu(), DELTA);
		Assertions.assertEquals(10, instance.getNbRequests());
		Assertions.assertEquals(1163.0, instance.getCost(), DELTA);
		Assertions.assertEquals(1744.5, instance.getMaxCost(), DELTA);
		Assertions.assertTrue(instance.getConstant());
		Assertions.assertEquals(10, instance.getMinQuantity());
		Assertions.assertEquals(15, instance.getMaxQuantity().intValue());
		Assertions.assertFalse(instance.isUnboundCost());
	}

	@Test
	void createProcessor() {
		final var vo = new QuoteFunctionEditionVo();
		vo.setSubscription(subscription);
		vo.setPrice(fpRepository.findByExpected("code", "FUNCTION3").getId());
		vo.setName("serverZ");
		vo.setNbRequests(10);
		vo.setProcessor("Intel");
		final var updatedCost = qfResource.create(vo);

		checkCost(updatedCost.getCost(), 168.0, 168.0, true);
		final var instance = qfRepository.findOneExpected(updatedCost.getId());
		Assertions.assertEquals(10, instance.getNbRequests());
		Assertions.assertEquals("Intel", instance.getProcessor());
		Assertions.assertEquals("Intel Xeon Platinum 8175 (Skylake)", instance.getPrice().getType().getProcessor());
		Assertions.assertEquals(1.5d, instance.getPrice().getCostRequests());
	}

	@Test
	void findInstanceTerms() {
		final var tableItem = qfResource.findPriceTerms(subscription, newUriInfo());
		Assertions.assertEquals(5, tableItem.getRecordsTotal());
		Assertions.assertEquals("on-demand1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstancePriceTermsNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qfResource.findPriceTerms(-1, uri));
	}

	@Test
	void findInstancePriceTermsAnotherSubscription() {
		final var uri = newUriInfo();
		Assertions.assertEquals(1,
				qfResource.findPriceTerms(getSubscription("mda", "service:prov:x"), uri).getData().size());
	}

	@Test
	void findInstancePriceTermsNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qfResource.findPriceTerms(subscription, uri));
	}

	@Test
	void findAllTypes() {
		final var tableItem = qfResource.findAllTypes(subscription, newUriInfo());
		Assertions.assertEquals(7, tableItem.getRecordsTotal());
		Assertions.assertEquals("function1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceCriteria() {
		final var tableItem = qfResource.findAllTypes(subscription, newUriInfo("unction1"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("function1", tableItem.getData().get(0).getName());
	}

	@Test
	void findInstanceNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qfResource.findAllTypes(-1, uri));
	}

	@Test
	void findInstanceAnotherSubscription() {
		Assertions.assertEquals(1,
				qfResource.findAllTypes(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findInstanceNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qfResource.findAllTypes(subscription, uri));
	}

	@Override
	protected FloatingCost updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(4702.958, cost.getMin(), DELTA);
		Assertions.assertEquals(6102.458, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4702.958, 6102.458, false);
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
		checkCost(status.getCost(), 4702.958, 6102.458, false);
		Assertions.assertEquals(7, status.getNbInstances());
		Assertions.assertEquals(2, status.getNbFunctions());
		Assertions.assertEquals(10.75d, status.getTotalCpu(), 0.0001); // 10.75 + 0 (Function)
		Assertions.assertEquals(45576, status.getTotalRam());
		Assertions.assertEquals(6, status.getNbPublicAccess());
		Assertions.assertEquals(4, status.getNbStorages());
		Assertions.assertEquals(104, status.getTotalStorage());
		Assertions.assertEquals("region-1", status.getLocation().getName());
	}
}
