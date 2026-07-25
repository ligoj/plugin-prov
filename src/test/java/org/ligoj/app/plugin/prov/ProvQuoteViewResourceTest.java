/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityNotFoundException;

/**
 * Test class of {@link ProvQuoteViewResource}.
 */
class ProvQuoteViewResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteViewResource viewResource;

	private int save(final String name, final String data) {
		final var vo = new ProvQuoteViewResource.QuoteViewEditionVo();
		vo.setName(name);
		vo.setData(data);
		return viewResource.save(subscription, vo);
	}

	@Test
	void saveListUpsertAndDelete() throws Exception {
		final var id = save("prod-eu", "{\"search\":\"web\"}");
		Assertions.assertEquals(1, viewResource.findAll(subscription).size());

		// Same name → replace, not duplicate.
		final var id2 = save("prod-eu", "{\"search\":\"db\"}");
		Assertions.assertEquals(id, id2);
		final var list = viewResource.findAll(subscription);
		Assertions.assertEquals(1, list.size());
		Assertions.assertEquals("{\"search\":\"db\"}", list.getFirst().getData());

		// Sorted by name, data included, no entity graph in the JSON.
		save("alpha", "{}");
		final var all = viewResource.findAll(subscription);
		Assertions.assertEquals("alpha", all.getFirst().getName());
		final var json = new ObjectMapper().findAndRegisterModules().writeValueAsString(all);
		Assertions.assertFalse(json.contains("\"refined\""));
		Assertions.assertTrue(json.contains("\"subscription\":" + subscription));

		viewResource.delete(subscription, id);
		Assertions.assertEquals(1, viewResource.findAll(subscription).size());
	}

	@Test
	void deleteChecksOwnership() {
		final var id = save("mine", "{}");
		final var foreign = getSubscription("mda", ProvResource.SERVICE_KEY);
		Assertions.assertThrows(EntityNotFoundException.class, () -> viewResource.delete(foreign, id));
	}
}
