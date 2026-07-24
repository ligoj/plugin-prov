/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.persistence.EntityNotFoundException;

/**
 * Test class of {@link ProvQuoteBulkResource}.
 */
class ProvQuoteBulkResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteBulkResource bulkResource;

	@BeforeEach
	void prepareCompute() throws IOException {
		persistEntities("csv/database",
				new Class<?>[] { ProvDatabaseType.class, ProvDatabasePrice.class, ProvQuoteDatabase.class },
				StandardCharsets.UTF_8);
		em.flush();
		em.clear();
	}

	private ProvQuoteInstance instance(final String name) {
		return qiRepository.findAll(getQuote()).stream().filter(i -> name.equals(i.getName())).findFirst()
				.orElseThrow();
	}

	private QuoteBulkEditionVo patch(final List<Integer> ids) {
		final var vo = new QuoteBulkEditionVo();
		vo.setIds(ids);
		return vo;
	}

	@Test
	void bulkApplyProfilesToInstances() {
		final var i1 = instance("server1");
		final var i2 = instance("server2");
		final var vo = patch(List.of(i1.getId(), i2.getId()));
		vo.setUsage("Dev");
		vo.setBudget("Dept2");
		vo.setOptimizer("CO2");

		bulkResource.update(subscription, ResourceType.INSTANCE, vo);
		em.flush();
		em.clear();

		for (final var name : new String[] { "server1", "server2" }) {
			final var entity = instance(name);
			Assertions.assertEquals("Dev", entity.getUsage().getName());
			Assertions.assertEquals("Dept2", entity.getBudget().getName());
			Assertions.assertEquals("CO2", entity.getOptimizer().getName());
		}
		// Untouched fields / resources stay as they were.
		Assertions.assertNull(instance("server3").getUsage());
	}

	@Test
	void bulkClearAndKeepSemantics() {
		final var i1 = instance("server1");
		final var set = patch(List.of(i1.getId()));
		set.setUsage("Dev");
		bulkResource.update(subscription, ResourceType.INSTANCE, set);

		// Empty string clears the override; absent (null) fields stay untouched.
		final var clear = patch(List.of(i1.getId()));
		clear.setUsage("");
		bulkResource.update(subscription, ResourceType.INSTANCE, clear);
		em.flush();
		em.clear();
		Assertions.assertNull(instance("server1").getUsage());
	}

	@Test
	void bulkLocationRepricesResourceAndStorages() {
		final var i1 = instance("server1");
		final var oldPrice = i1.getPrice().getCode();
		final var storagePrices = qsRepository.findAll(getQuote()).stream()
				.filter(s -> s.getQuoteInstance() != null && "server1".equals(s.getQuoteInstance().getName()))
				.map(s -> s.getPrice().getCode()).toList();
		Assertions.assertFalse(storagePrices.isEmpty());

		final var vo = patch(List.of(i1.getId()));
		vo.setLocation("region-2");
		final var total = bulkResource.update(subscription, ResourceType.INSTANCE, vo);
		em.flush();
		em.clear();

		final var patched = instance("server1");
		Assertions.assertEquals("region-2", patched.getLocation().getName());
		// The price was re-resolved under the new location.
		Assertions.assertNotEquals(oldPrice, patched.getPrice().getCode());
		Assertions.assertTrue(total.getMin() > 0);
	}

	@Test
	void bulkAppliesToDatabases() {
		final var db = qbRepository.findAll(getQuote()).stream().filter(d -> "database1".equals(d.getName()))
				.findFirst().orElseThrow();
		final var vo = patch(List.of(db.getId()));
		vo.setUsage("Dev");
		bulkResource.update(subscription, ResourceType.DATABASE, vo);
		em.flush();
		em.clear();
		Assertions.assertEquals("Dev", qbRepository.findById(db.getId()).orElseThrow().getUsage().getName());
	}

	@Test
	void bulkRejectsUnknownProfileAndForeignResource() {
		final var i1 = instance("server1");
		final var bad = patch(List.of(i1.getId()));
		bad.setUsage("no-such-profile");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> bulkResource.update(subscription, ResourceType.INSTANCE, bad));

		// A resource of another subscription's quote is rejected.
		final var foreign = qiRepository.findAll(repository.findBy("subscription.id",
				getSubscription("mda", ProvResource.SERVICE_KEY)));
		if (!foreign.isEmpty()) {
			final var vo = patch(List.of(foreign.getFirst().getId()));
			vo.setUsage("Dev");
			Assertions.assertThrows(EntityNotFoundException.class,
					() -> bulkResource.update(subscription, ResourceType.INSTANCE, vo));
		}
		// An unknown id too.
		final var unknown = patch(List.of(9_999_999));
		unknown.setUsage("Dev");
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> bulkResource.update(subscription, ResourceType.INSTANCE, unknown));
	}

	@Test
	void bulkDelete() {
		final var i1 = instance("server1");
		final var i2 = instance("server2");
		final var before = qiRepository.findAll(getQuote()).size();

		bulkResource.delete(subscription, ResourceType.INSTANCE, List.of(i1.getId(), i2.getId()));
		em.flush();
		em.clear();

		Assertions.assertEquals(before - 2, qiRepository.findAll(getQuote()).size());
		// Attached storages are gone with their parent.
		Assertions.assertTrue(qsRepository.findAll(getQuote()).stream()
				.noneMatch(s -> s.getQuoteInstance() != null && "server1".equals(s.getQuoteInstance().getName())));
	}
}
