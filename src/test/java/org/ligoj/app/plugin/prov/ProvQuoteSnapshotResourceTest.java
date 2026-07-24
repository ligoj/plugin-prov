/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSnapshotRepository;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

/**
 * Test class of {@link ProvQuoteSnapshotResource}.
 */
class ProvQuoteSnapshotResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteSnapshotResource snapResource;

	@Autowired
	private ProvQuoteSnapshotRepository snapRepository;

	@BeforeEach
	void prepareCompute() throws IOException {
		// Extend the base fixture (instances + storages) with the other compute types.
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

	private int snapshot(final String name) {
		final var vo = new org.ligoj.bootstrap.core.DescribedBean<Integer>();
		vo.setName(name);
		vo.setDescription(name + "-d");
		return snapResource.create(subscription, vo);
	}

	private int[] counts() {
		final var q = getQuote();
		return new int[] { qiRepository.findAll(q).size(), qbRepository.findAll(q).size(),
				qcRepository.findAll(q).size(), qfRepository.findAll(q).size(), qsRepository.findAll(q).size() };
	}

	@Test
	void createListAndDocument() throws Exception {
		final var id = snapshot("before-optim");
		final var list = snapResource.findAll(subscription);
		Assertions.assertEquals(1, list.size());
		final var snap = list.getFirst();
		Assertions.assertEquals(id, snap.getId());
		Assertions.assertEquals("before-optim", snap.getName());
		Assertions.assertTrue(snap.getCost() > 0);
		Assertions.assertTrue(snap.getNbResources() > 0);

		// The listing serialization stays light: no blob, no entity graph.
		final var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(list);
		Assertions.assertFalse(json.contains("\"data\""));
		Assertions.assertFalse(json.contains("\"refined\""));
		Assertions.assertTrue(json.contains("\"subscription\":" + subscription));

		// The document carries the normalized rows + profiles + defaults.
		final var document = snapResource.getDocument(subscription, id);
		Assertions.assertEquals(1, document.path("version").asInt());
		Assertions.assertEquals(snap.getNbResources(), document.withArray("resources").size());
		Assertions.assertFalse(document.withArray("usages").isEmpty());
		Assertions.assertEquals("Dept1", document.path("budget").asText());
		var foundInstance = false;
		for (final var row : document.withArray("resources")) {
			if ("instance".equals(row.path("resourceType").asText()) && "server1".equals(row.path("name").asText())) {
				foundInstance = true;
				Assertions.assertTrue(row.path("cost").asDouble() > 0);
				Assertions.assertFalse(row.path("typeName").asText().isEmpty());
				Assertions.assertFalse(row.path("term").asText().isEmpty());
			}
		}
		Assertions.assertTrue(foundInstance);
	}

	@Test
	void restoreRevertsToSnapshotState() {
		final var before = counts();
		final var id = snapshot("s1");

		// Devastate the quote: all compute + storages gone.
		qsResource.deleteAll(subscription);
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		qcResource.deleteAll(subscription);
		qfResource.deleteAll(subscription);
		em.flush();
		em.clear();
		Assertions.assertArrayEquals(new int[] { 0, 0, 0, 0, 0 }, counts());

		final var failed = snapResource.restore(subscription, id);
		Assertions.assertTrue(failed.isEmpty(), failed.toString());
		Assertions.assertArrayEquals(before, counts());

		// Quote defaults + profiles are back too.
		final var quote = getQuote();
		Assertions.assertEquals("Dept1", quote.getBudget().getName());
		Assertions.assertEquals("Cost", quote.getOptimizer().getName());
		Assertions.assertNotNull(usageRepository.findByName(subscription, "Full Time 12 month"));
		// Storages re-attached to their recreated parent.
		Assertions.assertTrue(qsRepository.findAll(quote).stream()
				.anyMatch(s -> s.getQuoteInstance() != null && "server1".equals(s.getQuoteInstance().getName())));
	}

	@Test
	void restoreRemovesResourcesAddedAfterSnapshot() {
		// S2 = state without server1: restoring S2 after restoring S1 must drop it again.
		final var s1 = snapshot("with-server1");
		final var server1 = qiRepository.findAll(getQuote()).stream().filter(i -> "server1".equals(i.getName()))
				.findFirst().orElseThrow();
		qiResource.delete(server1.getId());
		em.flush();
		em.clear();
		final var s2 = snapshot("without-server1");

		Assertions.assertTrue(snapResource.restore(subscription, s1).isEmpty());
		Assertions.assertTrue(
				qiRepository.findAll(getQuote()).stream().anyMatch(i -> "server1".equals(i.getName())));

		Assertions.assertTrue(snapResource.restore(subscription, s2).isEmpty());
		Assertions.assertTrue(
				qiRepository.findAll(getQuote()).stream().noneMatch(i -> "server1".equals(i.getName())));
	}

	@Test
	void restoreReportsUnmatchedRows() {
		final var id = snapshot("tampered");
		// Tamper the stored document so one row can no longer be priced: break both the exact
		// price code (fidelity path) and the requirements (lookup fallback path).
		final var entity = snapRepository.findOneExpected(id);
		// Unsatisfiable requirement scoped to server1's row — an impossible processor kills the
		// lookup fallback (the elastic "dynamic" type would swallow any cpu/ram tamper).
		final var procTampered = entity.getData()
				.replaceFirst("(\"name\":\"server1\".*?\"processor\":)null", "$1\"no-such-cpu\"");
		Assertions.assertNotEquals(entity.getData(), procTampered, "processor tamper missed");
		// Vanished exact price (kills the fidelity path).
		final var tampered = procTampered.replace("\"priceCode\":\"C1\"", "\"priceCode\":\"C1-GONE\"");
		Assertions.assertNotEquals(procTampered, tampered, "priceCode tamper missed");
		entity.setData(tampered);
		snapRepository.saveAndFlush(entity);

		final var failed = snapResource.restore(subscription, id);
		Assertions.assertTrue(failed.contains("server1"), failed.toString());
		// The rest of the quote is still restored.
		Assertions.assertTrue(qiRepository.findAll(getQuote()).stream().anyMatch(i -> "server2".equals(i.getName())));
	}

	@Test
	void deleteAndOwnershipCheck() {
		final var id = snapshot("gone");
		snapResource.delete(subscription, id);
		Assertions.assertTrue(snapResource.findAll(subscription).isEmpty());

		// A snapshot cannot be read through another subscription.
		final var other = snapshot("mine");
		final var foreign = getSubscription("mda", ProvResource.SERVICE_KEY);
		Assertions.assertThrows(EntityNotFoundException.class, () -> snapResource.getDocument(foreign, other));
	}
}
