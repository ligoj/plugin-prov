/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvComparisonRepository;
import org.ligoj.app.plugin.prov.dao.ProvLookupErrorRepository;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class of {@link ProvComparisonResource} — the cross-provider "compared subscriptions" feature. A comparison
 * cannot be hosted in a single {@code plugin-prov-*}: it needs two providers, both present in this module's fixture
 * ({@code service:prov:test} with a full catalog, and {@code service:prov:x} which has an instance type but no prices).
 * The MS (Jupiter) is loaded with instances + databases + containers + functions so every compute type is exercised.
 */
class ProvComparisonResourceTest extends AbstractProvResourceTest {

	private static final String CS_SAME = "service:prov:test:account";
	private static final String CS_OTHER = "service:prov:x:account";

	@Autowired
	private ProvComparisonResource cmpResource;

	@Autowired
	private ProvComparisonRepository cmpRepository;

	@Autowired
	private ProvLookupErrorRepository lookupErrorRepository;

	@BeforeEach
	void prepareCompute() throws IOException {
		// Add database / container / function resources (and their catalogs) to the MS quote (quote1).
		persistEntities("csv/database",
				new Class<?>[] { ProvDatabaseType.class, ProvDatabasePrice.class, ProvQuoteDatabase.class },
				StandardCharsets.UTF_8);
		persistEntities("csv/container",
				new Class<?>[] { ProvContainerType.class, ProvContainerPrice.class, ProvQuoteContainer.class },
				StandardCharsets.UTF_8);
		persistEntities("csv/function",
				new Class<?>[] { ProvFunctionType.class, ProvFunctionPrice.class, ProvQuoteFunction.class },
				StandardCharsets.UTF_8);
		em.flush();
		em.clear();
	}

	/** Create a fresh empty compared subscription (CS) on the given provider node, in the MS project. */
	private int newCs(final String nodeId) {
		final var sub = new Subscription();
		sub.setNode(em.find(Node.class, nodeId));
		sub.setProject(em.find(Subscription.class, subscription).getProject());
		em.persist(sub);

		final var quote = new ProvQuote();
		quote.setSubscription(sub);
		quote.setName("cs-" + nodeId);
		quote.setLocation(locationRepository.findAllBy("node.id", sub.getNode().getRefined().getId()).getFirst());
		em.persist(quote);
		em.flush();
		em.clear();
		return sub.getId();
	}

	private int nbInstances() {
		return qiRepository.findAll(getQuote()).size();
	}

	/** Total number of compute resources on the MS quote (all four types). */
	private int nbCompute() {
		final var q = getQuote();
		return qiRepository.findAll(q).size() + qbRepository.findAll(q).size() + qcRepository.findAll(q).size()
				+ qfRepository.findAll(q).size();
	}

	private long errorsOf(final int cs, final ResourceType type) {
		return lookupErrorRepository.findAllBySubscriptionId(cs).stream().filter(e -> e.getResourceType() == type)
				.count();
	}

	@Test
	void addComparedClonesEveryComputeTypeOntoSameProvider() {
		final var q = getQuote();
		final var nbI = qiRepository.findAll(q).size();
		final var nbB = qbRepository.findAll(q).size();
		final var nbC = qcRepository.findAll(q).size();
		final var nbF = qfRepository.findAll(q).size();
		Assertions.assertTrue(nbI > 0 && nbB > 0 && nbC > 0 && nbF > 0);

		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);

		Assertions.assertNotNull(cmpRepository.findByMainSubscriptionIdAndSubscriptionId(subscription, cs));

		// Every MS compute resource is either reproduced on the CS or recorded as an error
		// (a few can't be re-priced under the CS defaults, e.g. an instance needing 24 GB RAM).
		final var cfg = getConfiguration(cs);
		Assertions.assertEquals(nbI, cfg.getInstances().size() + errorsOf(cs, ResourceType.INSTANCE));
		Assertions.assertEquals(nbB, cfg.getDatabases().size() + errorsOf(cs, ResourceType.DATABASE));
		Assertions.assertEquals(nbC, cfg.getContainers().size() + errorsOf(cs, ResourceType.CONTAINER));
		Assertions.assertEquals(nbF, cfg.getFunctions().size() + errorsOf(cs, ResourceType.FUNCTION));

		// At least one of every type is reproduced (exercises each mirror success path).
		Assertions.assertFalse(cfg.getInstances().isEmpty());
		Assertions.assertFalse(cfg.getDatabases().isEmpty());
		Assertions.assertFalse(cfg.getContainers().isEmpty());
		Assertions.assertFalse(cfg.getFunctions().isEmpty());
		Assertions.assertTrue(cfg.getInstances().stream().allMatch(i -> i.getPrice() != null));
		Assertions.assertTrue(cfg.getCost().getMin() > 0);
	}

	@Test
	void addComparedCarriesQuoteAndResourceProfiles() {
		final var usage = usageRepository.findByName(subscription, "Full Time");
		final var budget = budgetRepository.findByName(subscription, "Dept1");
		final var optimizer = optimizerRepository.findByName(subscription, "Cost");
		final var quote = getQuote();
		quote.setUsage(usage);
		repository.saveAndFlush(quote);
		final var server1 = qiRepository.findAll(getQuote()).stream().filter(i -> "server1".equals(i.getName()))
				.findFirst().orElseThrow();
		server1.setUsage(usage);
		server1.setBudget(budget);
		server1.setOptimizer(optimizer);
		qiRepository.saveAndFlush(server1);
		em.flush();
		em.clear();

		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);
		em.flush();
		em.clear();

		// Quote-level default usage cloned + re-applied on the CS.
		Assertions.assertEquals("Full Time", repository.findBy("subscription.id", cs).getUsage().getName());
		// Resource-level usage / budget / optimizer carried onto the CS instance.
		final var csServer1 = qiRepository.findAll(repository.findBy("subscription.id", cs)).stream()
				.filter(i -> "server1".equals(i.getName())).findFirst().orElseThrow();
		Assertions.assertEquals("Full Time", csServer1.getUsage().getName());
		Assertions.assertEquals("Dept1", csServer1.getBudget().getName());
		Assertions.assertEquals("Cost", csServer1.getOptimizer().getName());
	}

	@Test
	void addComparedClonesProfilesAndDefaults() {
		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);
		em.flush();
		em.clear();

		Assertions.assertNotNull(budgetRepository.findByName(cs, "Dept1"));
		Assertions.assertNotNull(optimizerRepository.findByName(cs, "Cost"));
		final var csQuote = repository.findBy("subscription.id", cs);
		Assertions.assertEquals("Dept1", csQuote.getBudget().getName());
		Assertions.assertEquals("Cost", csQuote.getOptimizer().getName());
	}

	@Test
	void addComparedRecordsErrorsOnUnpriceableProvider() {
		final var expected = nbCompute();
		final var cs = newCs(CS_OTHER);
		cmpResource.addCompared(subscription, cs);

		Assertions.assertTrue(getConfiguration(cs).getInstances().isEmpty());

		final var errors = lookupErrorRepository.findAllBySubscriptionId(cs);
		Assertions.assertEquals(expected, errors.size());
		Assertions.assertTrue(errors.stream().allMatch(e -> e.getSubscription().getId() == cs
				&& e.getMainSubscription().getId() == subscription && e.getMainResourceId() != null));
		for (final var type : new ResourceType[] { ResourceType.INSTANCE, ResourceType.DATABASE, ResourceType.CONTAINER,
				ResourceType.FUNCTION }) {
			Assertions.assertTrue(errors.stream().anyMatch(e -> e.getResourceType() == type), "missing " + type);
		}
		Assertions.assertTrue(errors.stream().anyMatch(e -> "server1".equals(e.getName())));
		Assertions.assertEquals(expected, lookupErrorRepository.findAllByMainSubscriptionId(subscription).size());
	}

	@Test
	void addComparedWithoutQuoteDefaults() {
		// Clear the MS quote-level usage / budget / optimizer so the clone exercises the
		// "no default profile" branches.
		final var quote = getQuote();
		quote.setUsage(null);
		quote.setBudget(null);
		quote.setOptimizer(null);
		repository.saveAndFlush(quote);
		em.flush();
		em.clear();

		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);

		final var csQuote = repository.findBy("subscription.id", cs);
		Assertions.assertNull(csQuote.getUsage());
		Assertions.assertNull(csQuote.getBudget());
		Assertions.assertNull(csQuote.getOptimizer());
		Assertions.assertFalse(getConfiguration(cs).getInstances().isEmpty());
	}

	@Test
	void findAllSerializesErrorsWithoutSubscriptionGraph() throws Exception {
		final var cs = newCs(CS_OTHER);
		cmpResource.addCompared(subscription, cs);
		final var json = new ObjectMapper().writeValueAsString(cmpResource.findAll(subscription));

		// The lookup errors must NOT drag the whole Subscription → node → refined graph into
		// the payload (that produced a huge, malformed JSON). Subscriptions serialize as ids.
		Assertions.assertFalse(json.contains("\"refined\""), json.substring(0, Math.min(400, json.length())));
		Assertions.assertTrue(json.contains("\"subscription\":" + cs));
		Assertions.assertTrue(json.contains("\"mainSubscription\":" + subscription));
	}

	@Test
	void addComparedRejectsSelfComparison() {
		Assertions.assertThrows(IllegalArgumentException.class,
				() -> cmpResource.addCompared(subscription, subscription));
	}

	@Test
	void addComparedIsIdempotent() {
		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);
		cmpResource.addCompared(subscription, cs); // link already exists → no duplicate, re-clone
		Assertions.assertEquals(1, cmpRepository.findAllByMainSubscriptionId(subscription).size());
		Assertions.assertEquals(nbInstances(), getConfiguration(cs).getInstances().size());
	}

	@Test
	void findAllListsEveryComparedSubscription() {
		final var expectedErrors = nbCompute();
		final var csOk = newCs(CS_SAME);
		final var csErr = newCs(CS_OTHER);
		cmpResource.addCompared(subscription, csOk);
		cmpResource.addCompared(subscription, csErr);

		final var list = cmpResource.findAll(subscription);
		Assertions.assertEquals(2, list.size());

		final var ok = list.stream().filter(v -> v.getSubscription() == csOk).findFirst().orElseThrow();
		Assertions.assertTrue(ok.getCost().getMin() > 0);

		final var err = list.stream().filter(v -> v.getSubscription() == csErr).findFirst().orElseThrow();
		Assertions.assertEquals(expectedErrors, err.getErrors().size());
		// The same-provider CS reproduces more than the unpriceable one (which fails everything).
		Assertions.assertTrue(ok.getErrors().size() < err.getErrors().size());
	}

	@Test
	void removeComparedDropsLinkWipesAndClearsErrors() {
		final var cs = newCs(CS_OTHER);
		cmpResource.addCompared(subscription, cs);
		Assertions.assertFalse(lookupErrorRepository.findAllBySubscriptionId(cs).isEmpty());

		cmpResource.removeCompared(subscription, cs);

		Assertions.assertNull(cmpRepository.findByMainSubscriptionIdAndSubscriptionId(subscription, cs));
		Assertions.assertTrue(lookupErrorRepository.findAllBySubscriptionId(cs).isEmpty());
		Assertions.assertTrue(getConfiguration(cs).getInstances().isEmpty());
	}

	@Test
	void removeComparedWithoutLinkIsNoop() {
		final var cs = newCs(CS_SAME);
		// Never registered → no link; the call still wipes + clears without error.
		cmpResource.removeCompared(subscription, cs);
		Assertions.assertNull(cmpRepository.findByMainSubscriptionIdAndSubscriptionId(subscription, cs));
	}

	@Test
	void resyncReclonesFromCurrentMainState() {
		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);
		Assertions.assertEquals(nbInstances(), getConfiguration(cs).getInstances().size());

		qiResource.deleteAll(cs);
		Assertions.assertTrue(getConfiguration(cs).getInstances().isEmpty());

		cmpResource.resync(subscription);
		Assertions.assertEquals(nbInstances(), getConfiguration(cs).getInstances().size());
	}

	@Test
	void mirrorAllComputeTypesUpsertAndDelete() {
		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);
		final var before = getConfiguration(cs);
		final var nbI = before.getInstances().size();
		final var nbB = before.getDatabases().size();

		final var q = getQuote();
		final var instance = qiRepository.findAll(q).stream().filter(i -> "server1".equals(i.getName())).findFirst()
				.orElseThrow();
		final var database = qbRepository.findAll(q).getFirst();
		final var container = qcRepository.findAll(q).getFirst();
		final var function = qfRepository.findAll(q).getFirst();

		// Upsert each type (existing name replaced, not duplicated).
		cmpResource.mirrorResource(subscription, ResourceType.INSTANCE, instance.getId());
		cmpResource.mirrorResource(subscription, ResourceType.DATABASE, database.getId());
		cmpResource.mirrorResource(subscription, ResourceType.CONTAINER, container.getId());
		cmpResource.mirrorResource(subscription, ResourceType.FUNCTION, function.getId());
		Assertions.assertEquals(nbI, getConfiguration(cs).getInstances().size());
		Assertions.assertEquals(nbB, getConfiguration(cs).getDatabases().size());

		// Delete each type off the CS by name.
		cmpResource.mirrorDelete(subscription, ResourceType.INSTANCE, instance.getName());
		cmpResource.mirrorDelete(subscription, ResourceType.DATABASE, database.getName());
		cmpResource.mirrorDelete(subscription, ResourceType.CONTAINER, container.getName());
		cmpResource.mirrorDelete(subscription, ResourceType.FUNCTION, function.getName());

		final var after = getConfiguration(cs);
		Assertions.assertTrue(after.getInstances().stream().noneMatch(i -> instance.getName().equals(i.getName())));
		Assertions.assertTrue(after.getDatabases().stream().noneMatch(i -> database.getName().equals(i.getName())));
		Assertions.assertTrue(after.getContainers().stream().noneMatch(i -> container.getName().equals(i.getName())));
		Assertions.assertTrue(after.getFunctions().stream().noneMatch(i -> function.getName().equals(i.getName())));
	}

	@Test
	void mirrorGuardsAgainstNoLinkUnknownAndForeignResources() {
		final var server1 = qiRepository.findAll(getQuote()).stream().filter(i -> "server1".equals(i.getName()))
				.findFirst().orElseThrow();

		// No comparison yet → early return, nothing happens.
		cmpResource.mirrorResource(subscription, ResourceType.INSTANCE, server1.getId());

		final var cs = newCs(CS_SAME);
		cmpResource.addCompared(subscription, cs);

		// A non-compute type resolves to no compute resource → ignored.
		cmpResource.mirrorResource(subscription, ResourceType.STORAGE, server1.getId());
		// An unknown id → ignored.
		cmpResource.mirrorResource(subscription, ResourceType.INSTANCE, 9_999_999);
		// A resource belonging to another subscription (a CS instance) → ignored.
		final var csInstance = qiRepository.findAll(repository.findBy("subscription.id", cs)).getFirst();
		cmpResource.mirrorResource(subscription, ResourceType.INSTANCE, csInstance.getId());
		// A non-compute delete → no-op.
		cmpResource.mirrorDelete(subscription, ResourceType.STORAGE, "whatever");

		Assertions.assertEquals(nbInstances(), getConfiguration(cs).getInstances().size());
	}
}
