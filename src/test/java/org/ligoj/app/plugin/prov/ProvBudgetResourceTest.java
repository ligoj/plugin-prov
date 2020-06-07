/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class of {@link ProvUsageResource}
 */
public class ProvBudgetResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvUsageResource uResource;
	@Autowired
	private ProvBudgetResource bResource;

	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvBudgetRepository budgetRepository;

	@BeforeEach
	protected void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class },
				StandardCharsets.UTF_8.name());
		preparePostData();
	}

	@Test
	void create() {
		Assertions.assertEquals(13, resource.getConfiguration(subscription).getUsages().size());
		final var usage = new UsageEditionVo();
		usage.setName("DevV2");
		usage.setRate(75).setStart(6);
		usage.setConvertibleEngine(true).setConvertibleOs(true).setConvertibleLocation(true).setConvertibleFamily(true)
				.setConvertibleType(true).setReservation(true);
		final var id = uResource.create(subscription, usage);
		em.flush();
		em.clear();
		checkCost(subscription, 4704.758, 7154.358, false);
		resource.refresh(subscription);
		checkCost(subscription, 3165.4, 5615.0, false);

		final var entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(id, entity.getId().intValue());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(75, entity.getRate().intValue());
		Assertions.assertEquals(6, entity.getStart().intValue());
		Assertions.assertEquals(14, resource.getConfiguration(subscription).getUsages().size());
		Assertions.assertEquals(14, entity.getConfiguration().getUsages().size());
		Assertions.assertEquals(2, entity.getConfiguration().getBudgets().size());

		Assertions.assertTrue(entity.getConvertibleEngine());
		Assertions.assertTrue(entity.getConvertibleOs());
		Assertions.assertTrue(entity.getConvertibleLocation());
		Assertions.assertTrue(entity.getConvertibleFamily());
		Assertions.assertTrue(entity.getConvertibleType());
		Assertions.assertTrue(entity.getReservation());

		// Coverage only
		entity.getConfiguration().setUsages(Collections.emptyList());
	}

	@Test
	void updateSameInitialCost() {
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Full Time");
		quote.setBudget("Dept1");
		checkCost(resource.update(subscription, quote), 3165.4, 5615.0, false);
	}

	@Test
	void updateAttachedInstance() {
		attachUsageToQuote();

		// Add a database
		final var db = new ProvQuoteDatabase();
		db.setPrice(bpRepository.findBy("code", "MYSQL2"));
		db.setCpu(0.5);
		db.setRam(2000);
		db.setEngine("MYSQL");
		db.setName("qbMYSQL1");
		db.setConfiguration(repository.findByName("quote1"));
		em.persist(db);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3135.3, 4999.3, false);

		// Check the refresh has updated the database price
		Assertions.assertEquals(116.3, qbRepository.findByName("qbMYSQL1").getCost(), DELTA);
		Assertions.assertEquals("MYSQL1", qbRepository.findByName("qbMYSQL1").getPrice().getCode());

		// Usage -> 50% (attach the quote to a 50% usage)
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Dev");
		quote.setBudget("Dept1");
		checkCost(resource.update(subscription, quote), 1860.575, 3724.575, false);
		checkCost(subscription, 1860.575, 3724.575, false);
		em.flush();
		em.clear();

		// Check the refresh has updated the database price
		Assertions.assertEquals(58.15, qbRepository.findByName("qbMYSQL1").getCost(), DELTA);

		// Usage -> 75% (update the usage's rate from 50% to 75%)
		final var usage = new UsageEditionVo();
		usage.setId(usageRepository.findByName("Dev").getId());
		usage.setName("DevV2");
		usage.setRate(75);
		checkCost(uResource.update(subscription, usage).getTotal(), 2571.138, 4727.938, false);
		checkCost(subscription, 2571.138, 4727.938, false);

		final var entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(75, entity.getRate().intValue());
	}

	private void assertTermCount(final String name, final int count) {
		Assertions.assertEquals(count, resource.getConfiguration(subscription).getInstances().stream()
				.filter(i -> name.equals(i.getPrice().getTerm().getName())).count());
	}

	@Test
	void updateInitialCost() {
		updateAttachedInstance();
		var cost = resource.refresh(subscription);
		checkCost(subscription, 2571.138, 4727.938, false);
		Assertions.assertEquals(0, cost.getInitial(), DELTA);

		final var usage = new UsageEditionVo();
		usage.setId(usageRepository.findByName("DevV2").getId());
		usage.setName("DevV2");
		usage.setRate(100);
		usage.setDuration(12);
		usage.setConvertibleEngine(false).setConvertibleOs(false).setConvertibleLocation(false)
				.setConvertibleFamily(false).setConvertibleType(false).setReservation(false);
		cost = uResource.update(subscription, usage).getTotal();
		checkCost(cost, 3086.54, 5243.34, false);

		final var budget = new BudgetEditionVo();
		budget.setId(budgetRepository.findByName("Dept1").getId());
		budget.setName("Dept1");

		// Usage @100% /12Month to match with the term '1 year fragment'
		// Initial cost decomposition:
		// - C5_ = 2x 1317,6
		// - C12 = 1x 1229.76
		// - C11 = 1x 1229.76
		// = .........5094.72
		budget.setInitialCost(10000);
		cost = bResource.update(subscription, budget).getTotal();
		Assertions.assertEquals(10000, budgetRepository.findByName("Dept1").getInitialCost());
		checkCost(cost, 3086.54, 5243.34, false);
		assertTermCount("1y", 3);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);
		Assertions.assertEquals(15635.52, cost.getMaxInitial(), DELTA);

		// No change
		cost = bResource.update(subscription, budget).getTotal();
		checkCost(cost, 3086.54, 5243.34, false);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);

		// Reduce initial cost constraint (no more 1y term)
		budget.setInitialCost(1);
		cost = bResource.update(subscription, budget).getTotal();
		checkCost(cost, 3254.9, 5704.5, false);
		assertTermCount("1y", 0);
		Assertions.assertEquals(0, cost.getInitial(), DELTA);
		Assertions.assertEquals(0, cost.getMaxInitial(), DELTA);

		// Set the initial cost constraint above the optimal one
		budget.setInitialCost(5100);
		cost = bResource.update(subscription, budget).getTotal();
		checkCost(cost, 3086.54, 5243.34, false);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);

		// Set the initial cost constraint below the optimal one
		budget.setInitialCost(1320);
		cost = bResource.update(subscription, budget).getTotal();
		// TODO assertTermCount("1y", 1); -> C5
		assertTermCount("1y", 3);
		checkCost(cost, 3086.54, 5243.34, false);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);
		// TODO Assertions.assertEquals(1229.76, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "C5>x>(C11 or C12)" price
		budget.setInitialCost(1230);
		cost = bResource.update(subscription, budget).getTotal();
		// TODO assertTermCount("1y", 1); -> C11 or C12
		assertTermCount("1y", 2);
		checkCost(cost, 3159.74, 5609.34, false);
		Assertions.assertEquals(2459.52, cost.getInitial(), DELTA);
		// TODO Assertions.assertEquals(1229.76, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "OPTIM>x>(C11+C12)" price
		budget.setInitialCost(2460);
		cost = bResource.update(subscription, budget).getTotal();
		// TODO assertTermCount("1y", 2); -> C11 + C12
		assertTermCount("1y", 3);
		checkCost(cost, 3086.54, 5243.34, false);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);
		// TODO Assertions.assertEquals(2459,52, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "OPTIM>x>(2*C5+C11)" price
		budget.setInitialCost(3865);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 3);
		checkCost(cost, 3086.54, 5243.34, false);
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "C11"- price (no more 1y term)
		budget.setInitialCost(1228);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 0);
		checkCost(cost, 3254.9, 5704.5, false);
		Assertions.assertEquals(0, cost.getInitial(), DELTA);
	}

	private FloatingCost checkCost(final FloatingCost cost) {
		// Check the cost fully updated and exact actual cost
		Assertions.assertEquals(4704.758, cost.getMin(), DELTA);
		Assertions.assertEquals(7154.358, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4704.758, 7154.358, false);
		em.flush();
		em.clear();
		return cost;
	}

	@Test
	void findAll() {
		final var usages = uResource.findAll(subscription, newUriInfo());
		Assertions.assertEquals(10, usages.getData().size());
		Assertions.assertEquals("Dev", usages.getData().get(0).getName());
		Assertions.assertEquals("Dev 11 month", usages.getData().get(1).getName());
		Assertions.assertEquals(subscription,
				usages.getData().get(1).getConfiguration().getSubscription().getId().intValue());
	}

	@Test
	void deleteUsedInInstance() {
		attachUsageToQuote();
		final ProvUsage usage = usageRepository.findByName(subscription, "Dev");
		Assertions.assertNotNull(usage);
		Assertions.assertEquals(2, usageRepository.findAllBy("name", "Dev").size());

		// Delete the usage
		// Check the cost is now back at 100%
		checkUsage100AfterDelete(uResource.delete(subscription, usage.getId()));
	}

	@Test
	void deleteUnused() {
		final var quote = repository.findByName("quote1");
		quote.setUsage(usageRepository.findByName("Full Time"));
		em.persist(quote);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		Assertions.assertNotNull(usageRepository.findByName(subscription, "Dev"));
		Assertions.assertEquals(2, usageRepository.findAllBy("name", "Dev").size());

		// Delete the usage
		// Check the cost is at 100%
		checkUsage100AfterDelete(
				uResource.delete(subscription, usageRepository.findByName(subscription, "Dev").getId()));
	}

	@Test
	void deleteNotOwned() {
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> uResource.delete(0, usageRepository.findByName(subscription, "Dev").getId()));
	}

	@Test
	void deleteUsedInQuote() {
		// Usage = 100% by default and 50% fixed for "server1"
		final var server1 = qiRepository.findByName("server1");
		server1.setUsage(usageRepository.findByName("Dev"));
		em.persist(server1);
		checkCost(resource.refresh(subscription), 3019.0, 4883.0, false);

		// Usage = 50% by default and 50% fixed for "server1"
		final var quote = repository.findByName("quote1");
		quote.setUsage(usageRepository.findByName("Dev"));
		em.persist(quote);
		checkCost(resource.refresh(subscription), 1743.865, 3607.865, false);

		// Delete the usage
		// Check the cost is now back at 100%
		checkUsage100AfterDelete(
				uResource.delete(subscription, usageRepository.findByName(subscription, "Dev").getId()));

	}

	private void checkUsage100AfterDelete(final UpdatedCost cost) {
		checkCost(cost.getTotal(), 3165.4, 5615.0, false);
		checkCost(subscription, 3165.4, 5615.0, false);
		Assertions.assertNull(usageRepository.findByName(subscription, "Dev"));
		Assertions.assertEquals(1, usageRepository.findAllBy("name", "Dev").size());
	}

	private void attachUsageToQuote() {
		// Usage = 100%
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		// Usage = 100% by default, but fixed for some instances
		final var server1 = qiRepository.findByName("server1");
		server1.setUsage(usageRepository.findByName("Dev"));
		em.persist(server1);

		final var server2 = qiRepository.findByName("server2");
		server2.setUsage(usageRepository.findByName("Full Time"));
		em.persist(server2);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3019.0, 4883.0, false);
	}
}
