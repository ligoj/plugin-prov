/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
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
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvUsageResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvUsageResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private ProvResource resource;
	@Autowired
	private ProvUsageResource uResource;

	private int subscription;

	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvDatabasePriceRepository bpRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvQuoteRepository repository;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class,
						ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		clearAllCache();
		updateCost();
	}

	@Test
	void updateNotAttached() {
		final var usage = new UsageEditionVo();
		usage.setId(usageRepository.findByName("Full Time").getId());
		usage.setName("Full Time");
		usage.setRate(1);
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		checkCost(uResource.update(subscription, usage).getTotal(), 3165.4, 5615.0, false);
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		checkCost(resource.update(subscription, quote), 3165.4, 5615.0, false);
	}

	@Test
	void create() {
		Assertions.assertEquals(11, resource.getConfiguration(subscription).getUsages().size());
		final var usage = new UsageEditionVo();
		usage.setName("DevV2");
		usage.setRate(75);
		usage.setStart(6);
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
		Assertions.assertEquals(12, resource.getConfiguration(subscription).getUsages().size());
		Assertions.assertEquals(12, entity.getConfiguration().getUsages().size());
		entity.getConfiguration().setUsages(Collections.emptyList());
	}

	@Test
	void updateSameRate() {
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Full Time");
		checkCost(resource.update(subscription, quote), 3165.4, 5615.0, false);
	}

	@Test
	void updateRateAndQuote() {
		// Usage = 100%
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);

		// Usage -> 50% (attach the quote to a 50% usage)
		final var quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Dev");
		// Min = (3165.4 - 322.33 - 175.2[1y term])*.5 + 322.33 + 91.25 [1y term]
		checkCost(resource.update(subscription, quote), 1743.865, 3607.865, false);
		checkCost(subscription, 1743.865, 3607.865, false);
		em.flush();
		em.clear();

		// Usage -> 75% (update the usage's rate from 50% to 75%)
		var usage = new UsageEditionVo();
		usage.setId(usageRepository.findByName("Dev").getId());
		usage.setName("DevV2");
		usage.setRate(75);
		// Min = (3165.4 - 322.33 - 175.2[1y term])*.75 + 322.33 + 91.25 [1y term]
		checkCost(uResource.update(subscription, usage).getTotal(), 2454.633, 4611.433, false);
		checkCost(subscription, 2454.633, 4611.433, false);
		em.flush();
		em.clear();

		// Usage back to -> 100%
		usage.setRate(100);
		checkCost(uResource.update(subscription, usage).getTotal(), 3165.4, 5615.0, false);
		resource.refresh(subscription);

		// Usage -> duration extended to 12 month, the term is updated, cheapest monthly bill
		usage.setDuration(12);
		uResource.update(subscription, usage);
		checkCost(resource.refresh(subscription), 2982.4, 5139.2, false);

		final var entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(100, entity.getRate().intValue());
		Assertions.assertEquals(12, entity.getDuration());
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

	private FloatingCost updateCost() {
		// Check the cost fully updated and exact actual cost
		return checkCost(resource.updateCost(subscription));
	}

	private QuoteLightVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final var status = resource.getSubscriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private FloatingCost checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
		return cost;
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
