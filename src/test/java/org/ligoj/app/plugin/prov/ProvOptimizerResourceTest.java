/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvOptimizerRepository;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvOptimizer;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test class of {@link ProvUsageResource}
 */
class ProvOptimizerResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvOptimizerResource oResource;

	@Autowired
	private ProvOptimizerRepository optimizerRepository;

	@BeforeEach
	@Override
	protected void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvOptimizer.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class },
				StandardCharsets.UTF_8.name());
		preparePostData();
	}

	@Test
	void updateNotAttached() {
		final var optimizer = new OptimizerEditionVo();
		optimizer.setId(optimizerRepository.findByName("Cost").getId());
		optimizer.setName("Cost");
		optimizer.setMode(Optimizer.CO2);
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		checkCost(oResource.update(subscription, optimizer).getTotal(), 3165.4, 5615.0, false);
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		checkCost(resource.update(subscription, quote), 3165.4, 5615.0, false);
	}

	@Test
	void create() {
		Assertions.assertEquals(13, resource.getConfiguration(subscription).getUsages().size());
		final var optimizer = new OptimizerEditionVo();
		optimizer.setName("Cost2");
		optimizer.setMode(Optimizer.CO2);
		final var id = oResource.create(subscription, optimizer);
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

		Assertions.assertTrue(entity.getConvertibleEngine());
		Assertions.assertTrue(entity.getConvertibleOs());
		Assertions.assertTrue(entity.getConvertibleLocation());
		Assertions.assertTrue(entity.getConvertibleFamily());
		Assertions.assertTrue(entity.getConvertibleType());
		Assertions.assertTrue(entity.getReservation());

		// Coverage only
		entity.getConfiguration().setUsages(Collections.emptyList());
	}

	@Override
	protected Floating updateCost() {
		// Check the cost fully updated and exact actual cost
		return checkCost(resource.updateCost(subscription));
	}

	private Floating checkCost(final Floating cost) {
		// Check the cost fully updated and exact actual cost
		Assertions.assertEquals(4704.758, cost.getMin(), DELTA);
		Assertions.assertEquals(7154.358, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4704.758, 7154.358, false);
		em.flush();
		em.clear();
		return cost;
	}

}
