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
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false, 3151.15, 5551.15);
		checkCost(oResource.update(subscription, optimizer).getTotal(), 3165.4, 5615.0, false, 3151.15, 5551.15);
		Assertions.assertEquals("C1",
				resource.getConfiguration(subscription).getInstances().get(0).getPrice().getCode());
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setOptimizer("Cost");
		checkCost(resource.update(subscription, quote), 3371.285, 6644.426, false, 3049.835, 5044.576); // C1 -> C74
		Assertions.assertEquals("C74",
				resource.getConfiguration(subscription).getInstances().get(0).getPrice().getCode());
	}

	@Test
	void create() {
		Assertions.assertEquals(2, resource.getConfiguration(subscription).getOptimizers().size());
		final var optimizer = new OptimizerEditionVo();
		optimizer.setName("Co2_2");
		optimizer.setMode(Optimizer.CO2);
		oResource.create(subscription, optimizer);
		em.flush();
		em.clear();
		resource.refresh(subscription);
		checkCost(subscription, 3165.4, 5615.0, false);

		final var entity = optimizerRepository.findByName("Co2_2");
		Assertions.assertEquals("Co2_2", entity.getName());
		Assertions.assertEquals("CO2", entity.getMode().name());
		Assertions.assertEquals(Optimizer.CO2, entity.getMode());
		Assertions.assertEquals(3, resource.getConfiguration(subscription).getOptimizers().size());
		Assertions.assertEquals(3, entity.getConfiguration().getOptimizers().size());

		// Coverage only
		entity.getConfiguration().setOptimizers(Collections.emptyList());
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
