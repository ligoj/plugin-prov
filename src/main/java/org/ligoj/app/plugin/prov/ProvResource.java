/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;
import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.Configurable;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.dao.ProvCurrencyRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSupportRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.plugin.prov.quote.support.ProvQuoteSupportResource;
import org.ligoj.app.plugin.prov.terraform.TerraformRunnerResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The provisioning service. There is complete quote configuration along the subscription.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvResource extends AbstractConfiguredServicePlugin<ProvQuote> implements QuoteRelated<ProvQuote> {

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = BASE_URL + "/prov";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_KEY = SERVICE_URL.replace('/', ':').substring(1);

	/**
	 * Parameter used for attached currency.
	 */
	public static final String PARAMETER_CURRENCY_NAME = SERVICE_KEY + ":currency";

	/**
	 * Ordered/mapped columns.
	 */
	public static final Map<String, String> ORM_COLUMNS = new HashMap<>();

	/**
	 * Parallel configuration. When value is <code>1</code>, parallel stream will be used as often as possible.
	 * Otherwise, sequential will be used.
	 */
	public static final String USE_PARALLEL = SERVICE_KEY + ":use-parallel";

	@Autowired
	@Getter
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	@Getter
	private ProvQuoteRepository repository;

	@Autowired
	private ProvCurrencyRepository currencyRepository;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvTagResource tagResource;

	@Autowired
	private ProvNetworkResource networkResource;

	@Autowired
	private IamProvider[] iamProvider;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteDatabaseResource qbResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Autowired
	private ProvDatabaseTypeRepository btRepository;

	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvQuoteSupportRepository qs2Repository;

	@Autowired
	private ProvQuoteSupportResource qspResource;

	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvBudgetRepository budgetRepository;

	@Autowired
	private ProvBudgetResource budgetResource;

	@Autowired
	private TerraformRunnerResource runner;

	@Autowired
	private ProvResource self;

	static {
		ORM_COLUMNS.put("name", "name");
		ORM_COLUMNS.put("description", "description");
	}

	@Override
	public String getKey() {
		return SERVICE_KEY;
	}

	/**
	 * Transform a {@link String} to {@link UserOrg}
	 */
	private Function<String, ? extends UserOrg> toUser() {
		return iamProvider[0].getConfiguration().getUserRepository()::toUser;
	}

	/**
	 * Return all available locations for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the locations from the associated
	 *                     provider.
	 * @return The all available locations for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/location")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<ProvLocation> findLocations(@PathParam("subscription") final int subscription) {
		final var node = subscriptionResource.checkVisible(subscription).getNode().getId();
		return locationRepository.findAll(node);
	}

	/**
	 * Return the available processors for a subscription.
	 *
	 * @param node The node identifier, will be used to filter the processors from the associated provider.
	 * @return The available processors for the given subscription.
	 */
	@CacheResult(cacheName = "prov-processor")
	protected Map<String, List<String>> findProcessors(@CacheKey final String node) {
		final var listC = new HashMap<String, List<String>>();
		listC.put("instance", itRepository.findProcessors(node));
		listC.put("database", btRepository.findProcessors(node));
		return listC;
	}

	/**
	 * Check and return the expected location within the given subscription. The subscription is used to determinate the
	 * related node (provider). Return <code>null</code> when the given name is <code>null</code> or empty. In other
	 * cases, the the name must be found.
	 *
	 * @param node The provider node.
	 * @param name The location name. Case is insensitive.
	 * @return The visible location for the related subscription or <code>null</code>.
	 */
	public ProvLocation findLocation(final String node, final String name) {
		if (StringUtils.isEmpty(name)) {
			// No check
			return null;
		}
		// Find the scoped location
		return assertFound(locationRepository.findByName(node, name), name);
	}

	@GET
	@Path("{subscription:\\d+}")
	@Override
	public QuoteVo getConfiguration(@PathParam("subscription") final int subscription) {
		// Check the visibility
		return getConfiguration(subscriptionResource.checkVisible(subscription));
	}

	/**
	 * Return the quote configuration from a validated subscription. The subscription's visibility must have been
	 * previously checked.
	 *
	 * @param subscription A visible subscription for the current principal.
	 * @return The configuration with computed data.
	 */
	public QuoteVo getConfiguration(final Subscription subscription) {
		final var vo = new QuoteVo();
		final var quote = repository.getCompute(subscription.getId());
		DescribedBean.copy(quote, vo);
		vo.copyAuditData(quote, toUser());
		vo.setLocation(quote.getLocation());
		vo.setInstances(quote.getInstances());
		vo.setDatabases(qbRepository.findAll(quote));
		vo.setStorages(qsRepository.findAll(quote));
		vo.setUsage(quote.getUsage());
		vo.setBudget(quote.getBudget());
		vo.setLicense(quote.getLicense());
		vo.setRamAdjustedRate(ObjectUtils.defaultIfNull(quote.getRamAdjustedRate(), 100));
		vo.setReservationMode(quote.getReservationMode());
		vo.setProcessor(quote.getProcessor());
		vo.setPhysical(quote.getPhysical());
		vo.setTerraformStatus(runner.getTaskInternal(subscription));
		vo.setSupports(qs2Repository.findAll(quote));
		vo.setLocations(locationRepository.findAll(subscription.getNode().getId()));
		vo.setTags(tagResource.findAll(quote));
		vo.setNetworks(networkResource.findAll(subscription.getId()));
		vo.setUsages(usageRepository.findAll(quote));
		vo.setBudgets(budgetRepository.findAll(quote));
		vo.setProcessors(self.findProcessors(subscription.getNode().getTool().getId()));

		// Also copy the costs
		final var unbound = quote.isUnboundCost();
		vo.setCostNoSupport(new FloatingCost(quote.getCostNoSupport(), quote.getMaxCostNoSupport(),
				quote.getInitialCost(), quote.getMaxInitialCost(), unbound));
		vo.setCostSupport(new FloatingCost(quote.getCostSupport(), quote.getMaxCostSupport(), 0, 0, unbound));
		vo.setCost(quote.toFloatingCost());
		vo.setCurrency(quote.getCurrency());
		return vo;
	}

	/**
	 * Return the quote status linked to given subscription.
	 *
	 * @param subscription The parent subscription identifier.
	 * @return The quote status (summary only) linked to given subscription.
	 */
	public QuoteLightVo getSubscriptionStatus(final int subscription) {
		final var vo = new QuoteLightVo();
		final var compute = repository.getComputeSummary(subscription).get(0);
		final var database = repository.getDatabaseSummary(subscription).get(0);
		final var storage = repository.getStorageSummary(subscription).get(0);
		final var entity = (ProvQuote) compute[0];
		DescribedBean.copy(entity, vo);
		vo.setCost(entity.toFloatingCost());
		vo.setNbInstances(((Long) compute[1]).intValue());
		vo.setTotalCpu((Double) compute[2] + ((Double) database[2]).intValue());
		vo.setTotalRam(((Long) compute[3]).intValue() + ((Long) database[3]).intValue());
		vo.setNbPublicAccess(((Long) compute[4]).intValue() + ((Long) database[4]).intValue());
		vo.setNbDatabases(((Long) database[1]).intValue());
		vo.setNbStorages(((Long) storage[1]).intValue());
		vo.setTotalStorage(((Long) storage[2]).intValue());
		vo.setLocation(entity.getLocation());
		vo.setCurrency(entity.getCurrency());
		return vo;
	}

	/**
	 * Update the configuration details. The costs and the related resources are refreshed with lookup.
	 *
	 * @param subscription The subscription to update
	 * @param vo           The new quote.
	 * @return The new updated cost.
	 */
	@PUT
	@Path("{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost update(@PathParam("subscription") final int subscription, final QuoteEditionVo vo) {
		final var entity = getQuoteFromSubscription(subscription);
		entity.setName(vo.getName());
		entity.setDescription(vo.getDescription());

		var oldLicense = entity.getLicense();
		var oldLocation = entity.getLocation();
		var oldUsage = entity.getUsage();
		var oldBudget = entity.getBudget();
		var oldRamAdjusted = ObjectUtils.defaultIfNull(entity.getRamAdjustedRate(), 100);
		var oldReservationMode = ObjectUtils.defaultIfNull(entity.getReservationMode(), ReservationMode.RESERVED);
		var oldProcessor = StringUtils.trimToNull(entity.getProcessor());
		var oldPhysical = entity.getPhysical();
		entity.setLocation(findLocation(entity.getSubscription().getNode().getId(), vo.getLocation()));
		entity.setUsage(Optional.ofNullable(vo.getUsage())
				.map(u -> findConfiguredByName(usageRepository, u, subscription)).orElse(null));
		entity.setBudget(Optional.ofNullable(vo.getBudget())
				.map(u -> findConfiguredByName(budgetRepository, u, subscription)).orElse(null));
		entity.setLicense(vo.getLicense());
		entity.setRamAdjustedRate(ObjectUtils.defaultIfNull(vo.getRamAdjustedRate(), 100));
		entity.setReservationMode(vo.getReservationMode());
		entity.setProcessor(StringUtils.trimToNull(vo.getProcessor()));
		entity.setPhysical(vo.getPhysical());
		if (vo.isRefresh() || !oldLocation.equals(entity.getLocation()) || !Objects.equals(oldUsage, entity.getUsage())
				|| !Objects.equals(oldBudget, entity.getBudget()) || !oldRamAdjusted.equals(entity.getRamAdjustedRate())
				|| !oldReservationMode.equals(entity.getReservationMode())
				|| !Objects.equals(oldLicense, entity.getLicense())
				|| !Objects.equals(oldProcessor, entity.getProcessor())
				|| !Objects.equals(oldPhysical, entity.getPhysical())) {
			return refresh(entity);
		}

		// No refresh needed
		return entity.toFloatingCost();
	}

	/**
	 * Compute the total cost and save it into the related quote. All separated compute and storage costs are also
	 * updated.
	 *
	 * @param subscription The subscription to compute
	 * @return The updated computed cost.
	 */
	@PUT
	@Path("{subscription:\\d+}/refresh-cost")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost updateCost(@PathParam("subscription") final int subscription) {
		// Get the quote (and fetch instances) to refresh
		final var quote = repository.getCompute(subscription);
		return processCost(quote, BooleanUtils.isTrue(quote.getLeanOnChange())).getTotal();
	}

	/**
	 * Return a parallel stream if allowed.
	 * 
	 * @param <T>        The stream item type.
	 * @param collection The collection to stream.
	 * @return The parallel or sequential stream.
	 * @see #USE_PARALLEL
	 */
	public <T> Stream<T> newStream(final Collection<T> collection) {
		if (configuration.get(USE_PARALLEL, 1) == 1) {
			return collection.parallelStream();
		}
		return collection.stream();
	}

	/**
	 * For each resources, execute the given cost function.
	 */
	private UpdatedCost processCost(final ProvQuote entity, boolean lean) {
		final var relatedCosts = Collections
				.synchronizedMap(new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class));
		return processCost(entity, lean, relatedCosts);
	}

	/**
	 * For each resources, execute the given cost function.
	 */
	private UpdatedCost processCost(final ProvQuote entity, final boolean lean,
			Map<ResourceType, Map<Integer, FloatingCost>> relatedCosts) {
		if (lean) {
			budgetResource.lean(entity, relatedCosts);
			return processCost(entity, false, relatedCosts);
		}
		log.info("Refresh cost started for subscription {}", entity.getSubscription().getId());

		// Reset the costs to 0, will be updated further in this process
		entity.setCostNoSupport(0d);
		entity.setMaxCostNoSupport(0d);
		entity.setCost(0d);
		entity.setMaxCost(0d);
		entity.setInitialCost(0d);
		entity.setMaxInitialCost(0d);

		// Fetch the usages and budgets of this quotes (parallel)
		entity.getUsages().size();
		entity.getBudgets().size();

		// Add the compute cost, and update the unbound cost
		log.info("Refresh cost started for subscription {} / instances ... ", entity.getSubscription().getId());
		entity.setUnboundCostCounter((int) newStream(qiRepository.findAll(entity)).map(qiResource::updateCost)
				.map(fc -> addCost(entity, fc)).filter(FloatingCost::isUnbound).count());

		// Add the database cost
		log.info("Refresh cost started for subscription {} / databases ... ", entity.getSubscription().getId());
		newStream(qbRepository.findAll(entity)).map(qbResource::updateCost).forEach(fc -> addCost(entity, fc));

		// Add the storage cost
		log.info("Refresh cost started for subscription {} / storages ... ", entity.getSubscription().getId());
		newStream(qsRepository.findAll(entity)).map(qsResource::updateCost).forEach(fc -> addCost(entity, fc));

		// Return the rounded computation
		log.info("Refresh cost started for support {} / storages ... ", entity.getSubscription().getId());
		final var cost = new UpdatedCost(entity.getId());
		log.info("Refresh cost finished for subscription {}", entity.getSubscription().getId());
		cost.setRelated(relatedCosts);
		return refreshSupportCost(cost, entity);
	}

	private FloatingCost refreshSupportCost(final ProvQuote entity) {
		final var support = qs2Repository.findAll(entity).stream().map(qspResource::refresh)
				.reduce(new FloatingCost(0, 0, 0, 0, entity.isUnboundCost()), FloatingCost::add);
		entity.setCostSupport(round(support.getMin()));
		entity.setMaxCostSupport(round(support.getMax()));
		entity.setCost(round(entity.getCostSupport() + entity.getCostNoSupport()));
		entity.setMaxCost(round(entity.getMaxCostSupport() + entity.getMaxCostNoSupport()));
		return entity.toFloatingCost();
	}

	/**
	 * Refresh the cost of the support for the whole whole quote.
	 * 
	 * @param cost  The target cost object to update.
	 * @param quote The source quote.
	 * @return The same target cost parameter.
	 */
	public UpdatedCost refreshSupportCost(final UpdatedCost cost, final ProvQuote quote) {
		cost.setTotal(refreshSupportCost(quote).round());
		quote.getSupports().forEach(s -> cost.getRelated().computeIfAbsent(ResourceType.SUPPORT, k -> new HashMap<>())
				.put(s.getId(), s.toFloatingCost()));
		return cost;
	}

	/**
	 * Refresh the cost of the support for the whole whole quote related to a resource.
	 * 
	 * @param cost   The target cost object to update.
	 * @param entity A recently updated resource.
	 * @return The same target cost parameter.
	 */
	public <Q extends AbstractQuote<?>> UpdatedCost refreshSupportCost(final UpdatedCost cost, final Q entity) {
		return refreshSupportCost(cost, entity.getConfiguration());
	}

	/**
	 * Execute the lookup for each resource and compute the total cost.
	 *
	 * @param subscription The subscription to compute
	 * @return The updated computed cost.
	 */
	@PUT
	@Path("{subscription:\\d+}/refresh")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost refresh(@PathParam("subscription") final int subscription) {
		return refresh(getQuoteFromSubscription(subscription));
	}

	@Override
	public FloatingCost refresh(final ProvQuote entity) {
		updateCurrency(entity);
		return processCost(entity, true).getTotal();
	}

	/**
	 * Update the currency from the parameter.
	 */
	private void updateCurrency(final ProvQuote entity) {
		entity.setCurrency(Optional.ofNullable(
				subscriptionResource.getParameters(entity.getSubscription().getId()).get(PARAMETER_CURRENCY_NAME))
				.map(currencyRepository::findByName).orElse(null));
	}

	@Override
	public void create(final int subscription) {
		// Add an empty quote
		final var quote = new ProvQuote();
		quote.setSubscription(subscriptionRepository.findOne(subscription));

		// Associate a default name and description
		quote.setName(quote.getSubscription().getProject().getName());
		final var provider = quote.getSubscription().getNode().getRefined();
		final var locations = locationRepository.findAllBy("node.id", provider.getId());
		if (locations.isEmpty()) {
			// No available location, need a catalog to continue
			throw new BusinessException(SERVICE_KEY + "-no-catalog", provider.getId(), provider.getName());
		}

		quote.setLocation(locations.get(0));
		quote.setDescription(quote.getSubscription().getProject().getPkey() + "-> " + provider.getName());
		updateCurrency(quote);
		repository.saveAndFlush(quote);
	}

	/**
	 * Check the visibility of a configured entity and check the ownership by the given subscription.
	 *
	 * @param repository   The repository managing the entity to find.
	 * @param id           The requested configured identifier.
	 * @param subscription The required subscription owner.
	 * @param <K>          The {@link Configurable} identifier type.
	 * @param <T>          The {@link Configurable} type.
	 * @return The entity where the related subscription if visible.
	 */
	public <K extends Serializable, T extends Configurable<ProvQuote, K>> T findConfigured(
			final RestRepository<T, K> repository, final K id, final int subscription) {
		// Simple proxy call but with public visibility
		final var entity = findConfigured(repository, id);
		if (entity.getConfiguration().getSubscription().getId() != subscription) {
			// Associated project is not visible, reject the configuration access
			throw new EntityNotFoundException(id.toString());
		}
		return entity;
	}

	@Override
	public void delete(final int subscription, final boolean remoteData) {
		// Delete the configuration if available
		Optional.ofNullable(repository.findBy("subscription.id", subscription)).ifPresent(c -> repository.delete(c));
	}

	@Override
	public List<Class<?>> getInstalledEntities() {
		return Arrays.asList(Node.class, SystemConfiguration.class);
	}
}
