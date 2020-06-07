/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSupportRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvTagRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
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
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.plugin.prov.quote.support.ProvQuoteSupportResource;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvQuoteInstanceResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public abstract class AbstractProvResourceTest extends AbstractAppTest {

	protected static final double DELTA = 0.01d;

	protected int subscription;

	@Autowired
	protected ConfigurationResource configuration;

	@Autowired
	protected ProvResource resource;

	@Autowired
	protected ProvQuoteRepository repository;

	@Autowired
	private ProvTagRepository tagRepository;

	@Autowired
	protected ProvUsageRepository usageRepository;

	@Autowired
	protected ProvBudgetRepository budgetRepository;

	@Autowired
	protected ProvLocationRepository locationRepository;

	@Autowired
	protected ProvQuoteStorageRepository qsRepository;
	@Autowired
	protected ProvQuoteStorageResource qsResource;
	@Autowired
	protected ProvStorageTypeRepository stRepository;
	@Autowired
	protected ProvStoragePriceRepository spRepository;

	@Autowired
	protected ProvQuoteInstanceResource qiResource;
	@Autowired
	protected ProvQuoteInstanceRepository qiRepository;
	@Autowired
	protected ProvInstancePriceRepository ipRepository;
	@Autowired
	protected ProvInstanceTypeRepository itRepository;
	@Autowired
	protected ProvInstancePriceTermRepository iptRepository;

	@Autowired
	protected ProvQuoteDatabaseResource qbResource;
	@Autowired
	protected ProvQuoteDatabaseRepository qbRepository;
	@Autowired
	protected ProvDatabasePriceRepository bpRepository;
	@Autowired
	protected ProvDatabaseTypeRepository btRepository;

	@Autowired
	protected ProvQuoteSupportResource qs2Resource;
	@Autowired
	protected ProvQuoteSupportRepository qs2Repository;
	@Autowired
	protected ProvSupportPriceRepository sp2Repository;

	/**
	 * Prepare test data.
	 * 
	 * @throws IOException When CSV cannot be read.
	 */
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
		preparePostData();
	}

	protected void preparePostData() {
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		clearAllCache();
		// Set the default budget
		final var quote = repository.findBy("subscription.id", subscription);
		quote.setBudget(budgetRepository.findByName(subscription, "Dept1"));
		updateCost();
	}

	/**
	 * Flush the current JPA context and return the configuration of current subscription.
	 * 
	 * @return The configuration of current subscription.
	 */
	protected QuoteVo getConfiguration() {
		return getConfiguration(subscription);
	}

	/**
	 * Flush the current JPA context and return the configuration of given subscription.
	 * 
	 * @param subscription The subscription to get.
	 * @return The configuration of given subscription.
	 */
	protected QuoteVo getConfiguration(final int subscription) {
		em.flush();
		em.clear();
		return resource.getConfiguration(subscription);
	}

	/**
	 * Add two basic tags to the given object.
	 * 
	 * @param vo The object to complete with the new tags.
	 */
	protected void newTags(final QuoteTagSupport vo) {
		List<TagVo> tags = new ArrayList<>();
		final var tag1 = new TagVo();
		tag1.setName("name1");
		tag1.setValue("value1");
		tags.add(tag1);
		final var tag2 = new TagVo();
		tag2.setName("name2");
		tags.add(tag2);
		vo.setTags(tags);
	}

	/**
	 * Check the basics tags are associated to the given resource.
	 * 
	 * @param resource The resource to test.
	 */
	protected void assertTags(final AbstractQuote<?> resource) {
		Assertions.assertTrue(tagRepository
				.findAllBy("configuration.id", resource.getConfiguration().getId(), new String[] { "resource", "type" },
						resource.getId(), resource.getResourceType())
				.stream().allMatch(t -> "name1".equals(t.getName()) && "value1".equals(t.getValue())
						|| "name2".equals(t.getName()) && t.getValue() == null));
	}

	protected QuoteLightVo checkCost(final int subscription, final double min, final double max,
			final boolean unbound) {
		final var status = resource.getSubscriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	protected FloatingCost checkCost(final FloatingCost cost, final double min, final double max,
			final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
		return cost;
	}

	protected void checkCost(final UpdatedCost cost, final double min, final double max, final boolean unbound) {
		checkCost(cost.getTotal(), min, max, unbound);
	}

	protected FloatingCost updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(4704.758, cost.getMin(), DELTA);
		Assertions.assertEquals(7154.358, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 4704.758, 7154.358, false);
		em.flush();
		em.clear();
		return cost;
	}

}
