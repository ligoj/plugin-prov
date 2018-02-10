
package org.ligoj.app.plugin.prov;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.Configurable;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * The provisioning service. There is complete quote configuration along the subscription.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
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
	 * Ordered/mapped columns.
	 */
	static final Map<String, String> ORM_COLUMNS = new HashMap<>();

	@Autowired
	@Getter
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	@Getter
	private ProvQuoteRepository repository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	protected IamProvider[] iamProvider;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	private ProvQuoteInstanceResource instanceResource;

	@Autowired
	private ProvQuoteStorageResource storageResource;

	@Autowired
	private ProvUsageRepository usageRepository;

	static {
		ORM_COLUMNS.put("id", "id");
		ORM_COLUMNS.put("name", "name");
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

	@GET
	@Path("{subscription:\\d+}")
	@Override
	public QuoteVo getConfiguration(@PathParam("subscription") final int subscription) {
		return getConfiguration(subscriptionResource.checkVisibleSubscription(subscription));
	}

	/**
	 * Return the locations available for a subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the locations from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available locations for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/location")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvLocation> findLocations(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, locationRepository.findAll(subscription,
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Check and return the expected location within the given subscription. The subscription is used to determinate the
	 * related node (provider). Return <code>null</code> when the given name is <code>null</code> or empty. In other
	 * cases, the the name must be found.
	 * 
	 * @param subscription
	 *            A visible subscription for the current principal.
	 * @param name
	 *            The location name. Case is insensitive.
	 * @return The visible location for the related subscription or <code>null</code>.
	 */
	public ProvLocation findLocation(final int subscription, final String name) {
		if (StringUtils.isEmpty(name)) {
			// No check
			return null;
		}
		// Find the scoped location
		return assertFound(locationRepository.findByName(subscription, name), name);
	}

	/**
	 * Return the quote configuration from a validated subscription.
	 * 
	 * @param subscription
	 *            A visible subscription for the current principal.
	 * @return The configuration with computed data.
	 */
	public QuoteVo getConfiguration(final Subscription subscription) {
		final QuoteVo vo = new QuoteVo();
		final ProvQuote entity = repository.getCompute(subscription.getId());
		DescribedBean.copy(entity, vo);
		vo.copyAuditData(entity, toUser());
		vo.setLocation(Optional.ofNullable(entity.getLocation()).map(INamableBean::getName).orElse(null));
		vo.setInstances(entity.getInstances());
		vo.setStorages(repository.getStorage(subscription.getId()));
		vo.setUsage(entity.getUsage());
		// Also copy the pre-computed cost
		vo.setCost(toFloatingCost(entity));
		return vo;
	}

	/**
	 * Return the quote status linked to given subscription.
	 * 
	 * @param subscription
	 *            The parent subscription identifier.
	 * @return The quote status (summary only) linked to given subscription.
	 */
	public QuoteLigthVo getSusbcriptionStatus(final int subscription) {
		final QuoteLigthVo vo = new QuoteLigthVo();
		final Object[] compute = repository.getComputeSummary(subscription).get(0);
		final Object[] storage = repository.getStorageSummary(subscription).get(0);
		final ProvQuote entity = (ProvQuote) compute[0];
		DescribedBean.copy(entity, vo);
		vo.setCost(toFloatingCost(entity));
		vo.setNbInstances(((Long) compute[1]).intValue());
		vo.setTotalCpu((Double) compute[2]);
		vo.setTotalRam(((Long) compute[3]).intValue());
		vo.setNbPublicAccess(((Long) compute[4]).intValue());
		vo.setNbStorages(((Long) storage[1]).intValue());
		vo.setTotalStorage(((Long) storage[2]).intValue());
		vo.setLocation(entity.getLocation().getName());
		return vo;
	}

	/**
	 * Update the configuration details.
	 * 
	 * @param subscription
	 *            The subscription to update
	 * @param quote
	 *            The new quote.
	 * @return The new updated cost.
	 */
	@PUT
	@Path("{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost update(@PathParam("subscription") final int subscription, final QuoteEditionVo quote) {
		final ProvQuote entity = getQuoteFromSubscription(subscription);
		entity.setName(quote.getName());
		entity.setDescription(quote.getDescription());

		// TODO Check the location change to avoid useless compute
		entity.setLocation(findLocation(subscription, quote.getLocation()));
		entity.setUsage(Optional.ofNullable(quote.getUsage())
				.map(u -> findConfiguredByName(usageRepository, u, subscription)).orElse(null));
		return refreshCostAndResource(entity);
	}

	/**
	 * Compute the total cost and save it into the related quote. All separated compute and storage costs are also
	 * updated.
	 * 
	 * @param subscription
	 *            The subscription to compute
	 * @return The updated computed cost.
	 */
	@PUT
	@Path("{subscription:\\d+}/refresh-cost")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost refreshCost(@PathParam("subscription") final int subscription) {
		// Get the quote (and fetch instances) to refresh
		return refresh(repository.getCompute(subscription));
	}

	@Override
	public FloatingCost refresh(final ProvQuote entity) {
		// Reset the costs to 0, will be updated further in this process
		entity.setCost(0d);
		entity.setMaxCost(0d);

		// Add the compute cost, and update the unbound cost
		entity.setUnboundCostCounter((int) entity.getInstances().stream().map(instanceResource::updateCost)
				.map(fc -> addCost(entity, fc)).filter(FloatingCost::isUnbound).count());

		// Add the storage cost
		repository.getStorage(entity.getSubscription().getId()).stream().map(storageResource::updateCost)
				.forEach(fc -> addCost(entity, fc));
		return toFloatingCost(entity);
	}

	/**
	 * Execute the lookup for each resource and compute the total cost.
	 * 
	 * @param subscription
	 *            The subscription to compute
	 * @return The updated computed cost.
	 */
	@PUT
	@Path("{subscription:\\d+}/refresh")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost refreshCostAndResource(@PathParam("subscription") final int subscription) {
		return refreshCostAndResource(getQuoteFromSubscription(subscription));
	}

	/**
	 * Execute the lookup for each resource and compute the total cost.
	 * 
	 * @param quote
	 *            The quote to evaluate.
	 * @return The updated computed cost.
	 * @see #refreshCost(int)
	 */
	protected FloatingCost refreshCostAndResource(final ProvQuote quote) {
		// Update the instance, and add the cost
		quote.setUnboundCostCounter((int) quote.getInstances().stream().map(instanceResource::refresh)
				.map(fc -> addCost(quote, fc)).filter(FloatingCost::isUnbound).count());

		// Update the storage, and add the cost
		repository.getStorage(quote.getSubscription().getId()).stream().map(storageResource::refresh)
				.forEach(fc -> addCost(quote, fc));

		// Compute the the total cost
		return refreshCost(quote.getSubscription().getId());
	}

	@Override
	public void create(final int subscription) throws Exception {
		// Add an empty quote
		final ProvQuote quote = new ProvQuote();
		quote.setSubscription(subscriptionRepository.findOne(subscription));

		// Associate a default name and description
		quote.setName(quote.getSubscription().getProject().getName());
		final Node provider = quote.getSubscription().getNode().getRefined();
		final List<ProvLocation> locations = locationRepository.findAllBy("node.id", provider.getId());
		if (locations.isEmpty()) {
			// No available location, need a catalog to continue
			throw new BusinessException(SERVICE_KEY + "-no-catalog", provider.getId(), provider.getName());
		}
		quote.setLocation(locations.get(0));
		quote.setDescription(quote.getSubscription().getProject().getPkey() + "-> " + provider.getName());
		repository.saveAndFlush(quote);
	}

	@Override
	public <K extends Serializable, T extends Configurable<ProvQuote, K>> T findConfigured(
			final RestRepository<T, K> repository, final K id) {
		// Simple proxy call but with public visibility
		return super.findConfigured(repository, id);
	}

	/**
	 * Check the visibility of a configured entity and check the ownership by the given subscription.
	 * 
	 * @param id
	 *            The requested configured identifier.
	 * @param subscription
	 *            The required subscription owner.
	 * @param <K>
	 *            The {@link Configurable} identifier type.
	 * @param <T>
	 *            The {@link Configurable} type.
	 * @return The entity where the related subscription if visible.
	 */
	public <K extends Serializable, T extends Configurable<ProvQuote, K>> T findConfigured(
			final RestRepository<T, K> repository, final K id, final int subscription) {
		// Simple proxy call but with public visibility
		final T entity = findConfigured(repository, id);
		if (entity.getConfiguration().getSubscription().getId().intValue() != subscription) {
			// Associated project is not visible, reject the configuration access
			throw new EntityNotFoundException(id.toString());
		}
		return entity;
	}

	@Override
	public void delete(final int subscription, final boolean remoteData) {
		repository.delete(repository.findBy("subscription.id", subscription));
	}

}
