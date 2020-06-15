/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class of {@link ProvBudgetResource}
 */
class ProvBudgetResourceTest extends AbstractProvResourceTest {

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

		final var quote = repository.findBy("subscription.id", subscription);
		quote.setUsage(usageRepository.findByName(subscription, "Full Time 12 month"));
		em.flush();
		em.clear();
	}

	@Test
	void create() {
		resource.refresh(subscription);
		checkCost(subscription, 2982.4, 5139.2, false);
		Assertions.assertEquals(2, resource.getConfiguration(subscription).getBudgets().size());
		final var budget = new BudgetEditionVo();
		budget.setName("Dept3");
		budget.setInitialCost(100);
		final var id = bResource.create(subscription, budget);
		em.flush();
		em.clear();
		checkCost(subscription, 2982.4, 5139.2, false);
		resource.refresh(subscription);
		checkCost(subscription, 2982.4, 5139.2, false);

		final var entity = budgetRepository.findByName("Dept3");
		Assertions.assertEquals("Dept3", entity.getName());
		Assertions.assertEquals(id, entity.getId().intValue());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(100d, entity.getInitialCost(), DELTA);
		Assertions.assertEquals(3, resource.getConfiguration(subscription).getBudgets().size());

		// Coverage only
		entity.getConfiguration().setBudgets(Collections.emptyList());
	}

	@Test
	void updateSameInitialCost() {
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Full Time 12 month");
		quote.setBudget("Dept1");
		checkCost(resource.update(subscription, quote), 2982.4, 5139.2, false);
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
	}

	@Test
	void updateNoBudget() {
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
		checkCost(subscription, 2982.4, 5139.2, false);
		assertTermCount("1y", 4);

		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Full Time");
		quote.setBudget(null);
		checkCost(resource.update(subscription, quote), 3165.4, 5615.0, false);
		assertTermCount("1y", 0);
	}

	@Test
	void updateWithDatabases() throws IOException {
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
		checkCost(subscription, 2982.4, 5139.2, false);
		assertTermCount("1y", 4);

		persistEntities("csv/database", new Class[] { ProvQuoteDatabase.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		checkCost(resource.refresh(subscription), 5230.6, 7506.9, false);
		assertTermCount("1y", 4);
	}

	private void assertTermCount(final String name, final int count) {
		Assertions.assertEquals(count, resource.getConfiguration(subscription).getInstances().stream()
				.filter(i -> name.equals(i.getPrice().getTerm().getName())).count());
	}

	@Test
	void updateInitialCost() {
		var cost = resource.refresh(subscription);
		checkCost(subscription, 2982.4, 5139.2, false);

		// Initial cost decomposition:
		// - C5_ = 2x 1317,6
		// - C11 = 1x 1229.76
		// - C11 = 1x 1229.76
		// - C11 = 1x 1229.76
		// - C12 = 1x 1229.76
		// = .........6324.48
		Assertions.assertEquals(6324.48, cost.getInitial(), DELTA);
		Assertions.assertEquals(16865.28, cost.getMaxInitial(), DELTA);
		Assertions.assertEquals(1000000, budgetRepository.findByName("Dept1").getInitialCost());
		assertTermCount("1y", 4);

		// Reduce initial cost constraint (no more 1y term)
		final var budget = new BudgetEditionVo();
		budget.setId(budgetRepository.findByName("Dept1").getId());
		budget.setName("Dept1");
		budget.setInitialCost(0);
		cost = bResource.update(subscription, budget).getTotal();
		Assertions.assertEquals(0, budgetRepository.findByName("Dept1").getInitialCost());
		assertTermCount("1y", 0);
		checkCost(cost, 3165.4, 5615.0, false);
		Assertions.assertEquals(0, cost.getInitial(), DELTA);
		Assertions.assertEquals(0, cost.getMaxInitial(), DELTA);

		// Set the initial cost constraint above the optimal one
		budget.setInitialCost(6325);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 4);
		checkCost(subscription, 2982.4, 5139.2, false);
		Assertions.assertEquals(6324.48, cost.getInitial(), DELTA);

		// Set the initial cost constraint just below the optimal one
		budget.setInitialCost(6320);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 3); // -> C5 (quantity=2)+(C11 or C12)*2
		Assertions.assertEquals(5094.72, cost.getInitial(), DELTA);
		checkCost(subscription, 3015.34, 5172.14, false);

		// Reduce initial cost constraint adjusted to "C5>x>(C11 or C12)" price
		budget.setInitialCost(1230);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 1); // -> C11 or C12
		checkCost(cost, 3103.18, 5552.78, false);
		Assertions.assertEquals(1229.76, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "OPTIM>x>(C11 or C12)*2+C5" price
		budget.setInitialCost(3778);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 1); // -> C5 (quantity = 2)
		checkCost(subscription, 3092.2, 5249.0, false);
		Assertions.assertEquals(2635.2, cost.getInitial(), DELTA);

		// Reduce initial cost constraint adjusted to "C12"- price (no more 1y term)
		budget.setInitialCost(1228);
		cost = bResource.update(subscription, budget).getTotal();
		assertTermCount("1y", 0);
		checkCost(cost, 3165.4, 5615.0, false);
		Assertions.assertEquals(0, cost.getInitial(), DELTA);
	}

	@Test
	void findAll() {
		final var budgets = bResource.findAll(subscription, newUriInfo());
		Assertions.assertEquals(2, budgets.getData().size());
		Assertions.assertEquals("Dept1", budgets.getData().get(0).getName());
		Assertions.assertEquals("Dept2", budgets.getData().get(1).getName());
		Assertions.assertEquals(subscription,
				budgets.getData().get(1).getConfiguration().getSubscription().getId().intValue());
	}

	@Test
	void deleteUsedInInstance() {
		// Budget = Dept1 as default
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
		assertTermCount("1y", 4);

		// Budget = Dept2 for some instance
		final var server1 = qiRepository.findByName("server1");
		server1.setBudget(budgetRepository.findByName("Dept2"));
		em.persist(server1);

		final var server2 = qiRepository.findByName("server2");
		server2.setBudget(budgetRepository.findByName("Dept2"));
		em.persist(server2);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3070.24, 5519.84, false);
		assertTermCount("1y", 2); // 2 less servers

		final var budget = budgetRepository.findByName(subscription, "Dept1");
		Assertions.assertNotNull(budget);
		Assertions.assertEquals(2, budgetRepository.findAllBy("name", "Dept1").size());

		// Delete the budget
		checkBudgetAfterDelete(bResource.delete(subscription, budget.getId()));
	}

	@Test
	void deleteUnused() {
		final var quote = repository.findByName("quote1");
		quote.setBudget(budgetRepository.findByName("Dept2"));
		em.persist(quote);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		Assertions.assertNotNull(budgetRepository.findByName(subscription, "Dept2"));
		Assertions.assertEquals(2, budgetRepository.findAllBy("name", "Dept1").size());

		// Delete the usage
		// Check the cost is at 100%
		checkBudgetAfterDelete(
				bResource.delete(subscription, budgetRepository.findByName(subscription, "Dept1").getId()));
	}

	@Test
	void deleteNotOwned() {
		final var id = budgetRepository.findAllBy("name", "Dept1").stream()
				.filter(b -> b.getConfiguration().getSubscription().getId() != subscription).findFirst().get().getId();
		Assertions.assertThrows(EntityNotFoundException.class, () -> bResource.delete(0, id));
	}

	@Test
	void deleteUsedInQuote() {
		// Budget = 1000000
		final var server1 = qiRepository.findByName("server1");
		server1.setBudget(budgetRepository.findByName("Dept1"));
		em.persist(server1);
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);
		Assertions.assertEquals("C5", server1.getPrice().getCode());

		server1.setBudget(budgetRepository.findByName("Dept2"));
		em.persist(server1);
		checkCost(resource.refresh(subscription), 3055.6, 5505.2, false);
		Assertions.assertEquals("C1", server1.getPrice().getCode());

		// Delete the budget to go back the initial budget
		checkCost(bResource.delete(subscription, budgetRepository.findByName(subscription, "Dept2").getId()), 2982.4,
				5139.2, false);
		Assertions.assertEquals("C5", server1.getPrice().getCode());

	}

	@Test
	void leanNull() {
		final var relatedCosts = new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class);
		bResource.lean((ProvBudget) null, relatedCosts);
		Assertions.assertTrue(relatedCosts.isEmpty());
	}

	@Test
	void leanWithLog() {
		configuration.put("service:prov:log", "true");
		final var relatedCosts = new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class);
		bResource.lean(getQuote().getBudget(), relatedCosts);
		Assertions.assertFalse(relatedCosts.isEmpty());
	}

	@Test
	void leanNoBudget() {
		final var relatedCosts = new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class);
		final var quote = getQuote();
		quote.setBudget(null);
		em.merge(quote);
		em.flush();
		bResource.lean(quote, relatedCosts);
		Assertions.assertFalse(relatedCosts.isEmpty());
	}

	@Test
	void leanBudget0() {
		final var relatedCosts = new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class);
		final var budget = budgetRepository.findByName(subscription, "Dept1");
		budget.setInitialCost(0);
		em.merge(budget);
		em.flush();
		bResource.lean(getQuote(), relatedCosts);
		Assertions.assertFalse(relatedCosts.isEmpty());
	}

	@Test
	void logPackSlow() {
		bResource.logPack(0, Collections.emptyMap(), repository.findBy("subscription.id", subscription));
	}

	@Test
	void logPackFast() {
		bResource.logPack(System.currentTimeMillis(), Collections.emptyMap(),
				repository.findBy("subscription.id", subscription));
	}

	private void checkBudgetAfterDelete(final UpdatedCost cost) {
		checkCost(cost.getTotal(), 3165.4, 5615.0, false);
		checkCost(subscription, 3165.4, 5615.0, false);
		Assertions.assertNull(budgetRepository.findByName(subscription, "Dept1"));
		Assertions.assertEquals(1, budgetRepository.findAllBy("name", "Dept1").size());
	}

}
