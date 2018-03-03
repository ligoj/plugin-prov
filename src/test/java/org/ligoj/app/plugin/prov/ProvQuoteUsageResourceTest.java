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
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.bootstrap.core.json.TableItem;
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
public class ProvQuoteUsageResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ProvResource resource;
	@Autowired
	private ProvUsageResource uResource;

	private int subscription;

	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteRepository repository;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class,
						ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		refreshCost();
	}

	@Test
	public void updateNotAttached() {
		final UsageEditionVo usage = new UsageEditionVo();
		usage.setName("Full Time");
		usage.setRate(1);
		checkCost(resource.refreshCostAndResource(subscription), 3315.808, 5765.408, false);
		checkCost(uResource.update(subscription, "Full Time", usage).getTotalCost(), 3315.808, 5765.408, false);
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		checkCost(resource.update(subscription, quote), 3315.808, 5765.408, false);
	}

	@Test
	public void create() {
		final UsageEditionVo usage = new UsageEditionVo();
		usage.setName("DevV2");
		usage.setRate(75);
		final int id = uResource.create(subscription, usage);
		checkCost(subscription, 4704.758, 7154.358, false);
		resource.refreshCostAndResource(subscription);
		checkCost(subscription, 3315.808, 5765.408, false);

		final ProvUsage entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(id, entity.getId().intValue());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(75, entity.getRate().intValue());
		Assertions.assertEquals(9, entity.getConfiguration().getUsages().size());
		entity.getConfiguration().setUsages(Collections.emptyList());
	}

	@Test
	public void updateSameRate() {
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Full Time");
		checkCost(resource.update(subscription, quote), 3315.808, 5765.408, false);
	}

	@Test
	public void updateRateAndQuote() {
		// Usage = 100%
		checkCost(resource.refreshCostAndResource(subscription), 3315.808, 5765.408, false);

		// Usage -> 50% (attach the quote to a 50% usage)
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Dev");
		// Min = (3315.808 - 322.33 - 175.2[1y term])*.5 + 322.33 + 91.25 [1y
		// term]
		checkCost(resource.update(subscription, quote), 1906.909, 3770.909, false);
		checkCost(subscription, 1906.909, 3770.909, false);
		em.flush();
		em.clear();

		// Usage -> 75% (update the usage's rate from 50% to 75%)
		final UsageEditionVo usage = new UsageEditionVo();
		usage.setName("DevV2");
		usage.setRate(75);
		// Min = (3315.808 - 322.33 - 175.2[1y term])*.75 + 322.33 + 91.25 [1y
		// term]
		checkCost(uResource.update(subscription, "Dev", usage).getTotalCost(), 2611.359, 4768.159, false);
		checkCost(subscription, 2611.359, 4768.159, false);

		final ProvUsage entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(75, entity.getRate().intValue());
	}

	@Test
	public void updateAttachedInstance() {
		attachUsageToQuote();

		// Usage -> 50% (attach the quote to a 50% usage)
		final QuoteEditionVo quote = new QuoteEditionVo();
		quote.setName("any");
		quote.setLocation("region-1");
		quote.setUsage("Dev");
		checkCost(resource.update(subscription, quote), 1965.469, 3829.469, false);
		checkCost(subscription, 1965.469, 3829.469, false);
		em.flush();
		em.clear();

		// Usage -> 75% (update the usage's rate from 50% to 75%)
		final UsageEditionVo usage = new UsageEditionVo();
		usage.setName("DevV2");
		usage.setRate(75);
		checkCost(uResource.update(subscription, "Dev", usage).getTotalCost(), 2640.639, 4797.439, false);
		checkCost(subscription, 2640.639, 4797.439, false);

		final ProvUsage entity = usageRepository.findByName("DevV2");
		Assertions.assertEquals("DevV2", entity.getName());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(75, entity.getRate().intValue());
	}

	private QuoteLigthVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private FloatingCost checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
		return cost;
	}

	private FloatingCost refreshCost() {
		// Check the cost fully updated and exact actual cost
		return checkCost(resource.refreshCost(subscription));
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
	public void findAll() {
		final TableItem<ProvUsage> usages = uResource.findAll(subscription, newUriInfo());
		Assertions.assertEquals(8, usages.getData().size());
		Assertions.assertEquals("Dev", usages.getData().get(0).getName());
		Assertions.assertEquals("Dev 11 month", usages.getData().get(1).getName());
		Assertions.assertEquals(subscription,
				usages.getData().get(1).getConfiguration().getSubscription().getId().intValue());
	}

	@Test
	public void deleteUsedInInstance() {
		attachUsageToQuote();
		Assertions.assertNotNull(usageRepository.findByName(subscription, "Dev"));
		Assertions.assertEquals(2, usageRepository.findAllBy("name", "Dev").size());

		// Delete the usage
		// Check the cost is now back at 100%
		checkUsage100AfterDelete(uResource.delete(subscription, "Dev"));
	}

	@Test
	public void deleteUnused() {
		final ProvQuote quote = repository.findByName("quote1");
		quote.setUsage(usageRepository.findByName("Full Time"));
		em.persist(quote);
		em.flush();
		em.clear();
		checkCost(resource.refreshCostAndResource(subscription), 3315.808, 5765.408, false);

		Assertions.assertNotNull(usageRepository.findByName(subscription, "Dev"));
		Assertions.assertEquals(2, usageRepository.findAllBy("name", "Dev").size());

		// Delete the usage
		// Check the cost is at 100%
		checkUsage100AfterDelete(uResource.delete(subscription, "Dev"));
	}

	@Test
	public void deleteNotOwned() {
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			checkUsage100AfterDelete(uResource.delete(0, "Dev"));
		});
	}

	@Test
	public void deleteUsedInQuote() {
		// Usage = 100% by default and 50% fixed for "server1"
		final ProvQuoteInstance server1 = qiRepository.findByName("server1");
		server1.setUsage(usageRepository.findByName("Dev"));
		em.persist(server1);
		checkCost(resource.refreshCostAndResource(subscription), 3169.408, 5033.408, false);

		// Usage = 50% by default and 50% fixed for "server1"
		final ProvQuote quote = repository.findByName("quote1");
		quote.setUsage(usageRepository.findByName("Dev"));
		em.persist(quote);
		checkCost(resource.refreshCostAndResource(subscription), 1906.909, 3770.909, false);

		// Delete the usage
		// Check the cost is now back at 100%
		checkUsage100AfterDelete(uResource.delete(subscription, "Dev"));

	}

	private void checkUsage100AfterDelete(final UpdatedCost cost) {
		checkCost(cost.getTotalCost(), 3315.808, 5765.408, false);
		checkCost(subscription, 3315.808, 5765.408, false);
		Assertions.assertNull(usageRepository.findByName(subscription, "Dev"));
		Assertions.assertEquals(1, usageRepository.findAllBy("name", "Dev").size());
	}

	private void attachUsageToQuote() {
		// Usage = 100%
		checkCost(resource.refreshCostAndResource(subscription), 3315.808, 5765.408, false);

		// Usage = 100% by default, but fixed for some instances
		final ProvQuoteInstance server1 = qiRepository.findByName("server1");
		server1.setUsage(usageRepository.findByName("Dev"));
		em.persist(server1);

		final ProvQuoteInstance server2 = qiRepository.findByName("server2");
		server2.setUsage(usageRepository.findByName("Full Time"));
		em.persist(server2);
		em.flush();
		em.clear();
		checkCost(resource.refreshCostAndResource(subscription), 3169.408, 5033.408, false);
	}
}
