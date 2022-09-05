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
 * Test class of {@link ProvOptimizerResource}
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
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false, 1331.82, 3795.82);
		em.flush();
		em.clear();

		// Change, unattached optimizer profile
		final var optimizer = new OptimizerEditionVo();
		optimizer.setId(optimizerRepository.findByName("CO2").getId());
		optimizer.setName("CO2New");
		optimizer.setMode(Optimizer.CO2);
		checkCost(oResource.update(subscription, optimizer).getTotal(), 3165.4, 5615.0, false, 1331.82, 3795.82);
		var instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C1", instance.getPrice().getCode());
		Assertions.assertEquals(600.0, instance.getCo2());
	}

	@Test
	void optimizeCo2() {
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false, 1331.82, 3795.82);
		em.flush();
		em.clear();
		var instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C1", instance.getPrice().getCode());
		Assertions.assertEquals(600.0, instance.getCo2());
		Assertions.assertEquals(292.8, instance.getCost());

		// Attach the CO2 profile as default optimizer of this quote
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setOptimizer("CO2");
		checkCost(resource.update(subscription, quote), 3485.208, 6758.349, false, 901.6, 2014.4); // C1 -> C74
		checkCost(resource.refresh(subscription), 3485.208, 6758.349, false, 901.6, 2014.4);

		// C74 CO2: ram=1.3g/GiB, cpu=128.5g/cpu, minCpu = 1.0
		// server1: cpu=1, ram=2000, minQuantity=2
		// Total = (128.5+1.3*2)*2 = 262.2
		instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C74", instance.getPrice().getCode());
		Assertions.assertEquals(262.2, instance.getCo2());

		// Change the Watt profile to a ~linear function
		instance.setWorkload("80,40@50,60@100");
		em.persist(instance);
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3485.208, 6758.349, false, 862.0, 1816.4);

		// C74 CO2: ram=0.8g@50% & 1.3@100%/GiB, cpu=80g@50% & 128.5@100%/cpu, minCpu = 1.0
		// server1: cpu=1, ram=2000, minQuantity=2
		// Total = (cpu:(80*0.4+128.5*0.6) +ram:(0.8*0.4+1.3*0.6)*2)*2 = 111.3 *2 = 222,6
		instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals(222.6d, instance.getCo2());

		// Remove a specific quote instance to define a valid configuration
		qiRepository.deleteAllBy("name", "server7");
		checkCost(resource.refresh(subscription), 2678.6, 5951.741, false, 776.5, 1730.9);

		// Delete CO2 data from a specific price (C74), it becomes excluded from CO2 optimization lookup
		instance.getPrice().getType().setWatt(0d);
		em.persist(instance.getPrice().getType());
		em.flush();
		em.clear();
		clearAllCache();
		checkCost(resource.refresh(subscription), 4033.57, 7654.37, false, 3728.12, 6134.52);
		instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C13", instance.getPrice().getCode());
		Assertions.assertEquals(585.6, instance.getCo2());
		Assertions.assertEquals(585.6, instance.getCost());

		// Delete all CO2 data from intance types to sitxh to COST mode when there is no CO2 data
		em.createQuery("UPDATE ProvInstanceType SET watt=0").executeUpdate();
		em.flush();
		clearAllCache();
		checkCost(resource.refresh(subscription), 2358.792, 4808.392, false, 1246.32, 3710.32);
		instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C1", instance.getPrice().getCode());
		Assertions.assertEquals(600.0, instance.getCo2());
		Assertions.assertEquals(292.8, instance.getCost());
	}

	@Test
	void updateAttachedInstance() {
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false, 1331.82, 3795.82);
		var instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C1", instance.getPrice().getCode());
		instance.setOptimizer(optimizerRepository.findByName(subscription, "CO2"));
		em.flush();
		em.clear();
		checkCost(resource.refresh(subscription), 3371.285, 6644.426, false, 994.02, 2106.82); // C1 -> C74
		instance = findByName(resource.getConfiguration(subscription).getInstances(), "server1");
		Assertions.assertEquals("C74", instance.getPrice().getCode());
		Assertions.assertEquals(262.2, instance.getCo2());
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
