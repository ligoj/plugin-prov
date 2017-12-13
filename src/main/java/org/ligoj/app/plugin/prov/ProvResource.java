
package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The provisioning service. There is complete quote configuration along the
 * subscription.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvResource extends AbstractConfiguredServicePlugin<ProvQuote> {

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = BASE_URL + "/prov";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_KEY = SERVICE_URL.replace('/', ':').substring(1);
	private static final String[] DEFAULT_COLUMNS = { "name", "cpu", "ram", "os", "disk", "frequency", "optimized" };
	private static final String[] ACCEPTED_COLUMNS = { "name", "cpu", "ram", "constant", "os", "disk", "frequency", "optimized", "term",
			"type", "internet", "maxCost", "minQuantity", "maxQuantity", "maxVariableCost", "ephemeral", "location" };

	/**
	 * Average hours in one month.
	 */
	private static final double HOURS_BY_MONTH = 24d * 365d / 12d;

	/**
	 * Ordered/mapped columns.
	 */
	private static final Map<String, String> ORM_COLUMNS = new HashMap<>();

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private CsvForBean csvForBean;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvInstancePriceTermRepository iptRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstanceTypeRepository instanceRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	protected IamProvider[] iamProvider;

	@Autowired
	private PaginationJson paginationJson;

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
	 *            The subscription identifier, will be used to filter the locations
	 *            from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available locations for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/location")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvLocation> findLocations(@PathParam("subscription") final int subscription, @Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, locationRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
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
		// Also copy the pre-computed cost
		vo.setCost(toFloatingCost(entity));
		return vo;
	}

	/**
	 * Return the quote associated to the given subscription. The visibility is
	 * checked.
	 * 
	 * @param subscription
	 *            The linked subscription.
	 * @return The quote if the visibility has been checked.
	 */
	private ProvQuote getQuoteFromSubscription(final int subscription) {
		return repository.findBy("subscription", subscriptionResource.checkVisibleSubscription(subscription));
	}

	/**
	 * Create the instance inside a quote.
	 * 
	 * @param vo
	 *            The quote instance.
	 * @return The created instance cost details with identifier.
	 */
	@POST
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost createInstance(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(new ProvQuoteInstance(), vo);
	}

	/**
	 * Update the instance inside a quote.
	 * 
	 * @param vo
	 *            The quote instance to update.
	 * @return The new cost configuration.
	 */
	@PUT
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost updateInstance(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(findConfigured(qiRepository, vo.getId()), vo);
	}

	/**
	 * Save or update the given entity from the {@link QuoteInstanceEditionVo}. The
	 * computed cost are recursively updated from the instance to the quote total
	 * cost.
	 */
	private UpdatedCost saveOrUpdate(final ProvQuoteInstance entity, final QuoteInstanceEditionVo vo) {
		// Compute the unbound cost delta
		final int deltaUnbound = BooleanUtils.toInteger(vo.getMaxQuantity() == null) - BooleanUtils.toInteger(entity.isUnboundCost());

		// Check the associations and copy attributes to the entity
		final ProvQuote configuration = getQuoteFromSubscription(vo.getSubscription());
		entity.setConfiguration(configuration);
		final Subscription subscription = configuration.getSubscription();
		final String providerId = subscription.getNode().getRefined().getId();
		DescribedBean.copy(vo, entity);
		entity.setPrice(ipRepository.findOneExpected(vo.getPrice()));
		entity.setLocation(findLocation(subscription.getId(), vo.getLocation()));
		entity.setOs(ObjectUtils.defaultIfNull(vo.getOs(), entity.getPrice().getOs()));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		entity.setConstant(vo.getConstant());
		entity.setEphemeral(vo.isEphemeral());
		entity.setInternet(vo.getInternet());
		entity.setMaxVariableCost(vo.getMaxVariableCost());
		entity.setMinQuantity(vo.getMinQuantity());
		entity.setMaxQuantity(vo.getMaxQuantity());

		checkVisibility(entity.getPrice().getType(), providerId);
		checkConstraints(entity);
		checkOs(entity);

		// Update the unbound increment of the global quote
		configuration.setUnboundCostCounter(configuration.getUnboundCostCounter() + deltaUnbound);

		// Save and update the costs
		final UpdatedCost cost = newUpdateCost(qiRepository, entity, this::updateCost);
		final Map<Integer, FloatingCost> storagesCosts = new HashMap<>();
		CollectionUtils.emptyIfNull(entity.getStorages()).forEach(s -> storagesCosts.put(s.getId(), addCost(s, this::updateCost)));
		cost.setRelatedCosts(storagesCosts);
		cost.setTotalCost(toFloatingCost(entity.getConfiguration()));
		return cost;
	}

	/**
	 * Check the requested OS is compliant with the one of associated
	 * {@link ProvInstancePrice}
	 */
	private void checkOs(ProvQuoteInstance entity) {
		if (entity.getOs().toPricingOs() != entity.getPrice().getOs()) {
			// Incompatible, hack attempt?
			log.warn("Attempt to create an instance with an incompatible OS {} with catalog OS {}", entity.getOs(),
					entity.getPrice().getOs());
			throw new ValidationJsonException("os", "incompatible-os", entity.getPrice().getOs());
		}
	}

	/**
	 * Extract the costs from a quote and build a new {@link FloatingCost}
	 * 
	 * @param configuration
	 *            The quote configuration.
	 * @return The built {@link FloatingCost} instance.
	 */
	public FloatingCost toFloatingCost(final ProvQuote configuration) {
		return new FloatingCost(configuration.getCost(), configuration.getMaxCost(), configuration.getUnboundCostCounter() > 0);
	}

	private void checkConstraints(ProvQuoteInstance entity) {
		if (entity.getMaxQuantity() != null && entity.getMaxQuantity() < entity.getMinQuantity()) {
			// Maximal quantity must be greater than minimal quantity
			throw new ValidationJsonException("maxQuantity", "Min", entity.getMinQuantity());
		}
	}

	/**
	 * Add a cost to the quote related to given resource entity. The global cost is
	 * not deeply computed, only delta is applied.
	 * 
	 * @param entity
	 *            The configured entity, related to a quote.
	 * @param costUpdater
	 *            The function used to compute the new cost.
	 * @return The new computed cost.
	 */
	private <T extends Costed> FloatingCost addCost(final T entity, final Function<T, FloatingCost> costUpdater) {
		// Save the previous costs
		final double oldCost = ObjectUtils.defaultIfNull(entity.getCost(), 0d);
		final double oldMaxCost = ObjectUtils.defaultIfNull(entity.getMaxCost(), 0d);

		// Process the update of this entity
		final FloatingCost newCost = costUpdater.apply(entity);

		// Report the delta to the quote
		final ProvQuote configuration = entity.getConfiguration();
		configuration.setCost(round(configuration.getCost() + entity.getCost() - oldCost));
		configuration.setMaxCost(round(configuration.getMaxCost() + entity.getMaxCost() - oldMaxCost));
		return newCost;
	}

	/**
	 * Delete all instances from a quote. The total cost is updated.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost deleteAllInstances(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);

		// Delete all instance with cascaded delete for storages
		qiRepository.deleteAll(qiRepository.findAllBy("configuration.subscription.id", subscription));

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of instances cost and related storage costs
		return refreshCost(subscription);
	}

	/**
	 * Delete all storages from a quote. The total cost is updated.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("{subscription:\\d+}/storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost deleteAllStorages(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);

		// Delete all storages related to any instance, then the instances
		qsRepository.deleteAll(qsRepository.findAllBy("configuration.subscription.id", subscription));

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of storage costs.
		return refreshCost(subscription);
	}

	/**
	 * Create the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage details.
	 * @return The created instance cost details with identifier.
	 */
	@POST
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost createStorage(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(new ProvQuoteStorage(), vo);
	}

	/**
	 * Update the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage update.
	 * @return The new cost configuration.
	 */
	@PUT
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost updateStorage(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(findConfigured(qsRepository, vo.getId()), vo);
	}

	/**
	 * Check and return the expected location within the given subscription. The
	 * subscription is used to determinate the related node (provider). Return
	 * <code>null</code> when the given name is <code>null</code> or empty. In other
	 * cases, the the name must be found.
	 */
	private ProvLocation findLocation(final int subscription, final String name) {
		if (StringUtils.isEmpty(name)) {
			// No check
			return null;
		}
		// Find the scoped location
		return assertFound(locationRepository.findByName(subscription, name), name);
	}

	/**
	 * Check and return the non null object.
	 */
	private <T> T assertFound(final T object, final String name) {
		// Find the scoped location
		return Optional.ofNullable(object).orElseThrow(() -> new EntityNotFoundException(name));
	}

	private FloatingCost refreshInstance(final ProvQuoteInstance instance) {
		final ProvQuote quote = instance.getConfiguration();

		// Find the lowest price
		final ProvInstancePrice price = validateLookup("instance",
				lookupInstance(quote, instance.getCpu(), instance.getRam(), instance.getConstant(), instance.getOs(), null,
						instance.getPrice().getTerm().getId(), instance.isEphemeral(),
						Optional.ofNullable(instance.getLocation()).map(INamableBean::getName).orElse(null)),
				instance.getName());
		instance.setPrice(price);
		return updateCost(instance);
	}

	private FloatingCost refreshStorage(final ProvQuoteStorage storage) {
		final ProvQuote quote = storage.getConfiguration();

		// Find the lowest price
		final ProvStoragePrice price = storage.getPrice();
		final Integer qi = Optional.ofNullable(storage.getQuoteInstance()).map(ProvQuoteInstance::getId).orElse(null);
		final String location = Optional.ofNullable(storage.getLocation()).map(INamableBean::getName).orElse(null);
		storage.setPrice(validateLookup("storage",
				lookupStorage(quote, storage.getSize(), price.getType().getFrequency(), qi, price.getType().getOptimized(), location)
						.stream().findFirst().orElse(null),
				storage.getName()));
		return updateCost(storage);
	}

	/**
	 * Check the lookup succeed.
	 */
	private <T extends AbstractPrice<? extends AbstractNamedEntity<?>>> T validateLookup(final String resourceType,
			final AbstractComputedPrice<T> lookup, final String context) {
		if (lookup == null) {
			throw new ValidationJsonException(resourceType, "no-match-" + resourceType, "resource", context);
		}
		final T instancePrice = lookup.getPrice();
		return instancePrice;
	}

	/**
	 * Check and return the storage matching to the requirements
	 */
	private ProvStoragePrice findStorage(final int subscription, final String name, final String location, final ProvQuote quote) {
		return assertFound(spRepository.findByName(subscription, name, Optional.ofNullable(location).orElse(quote.getLocation().getName())),
				name);
	}

	/**
	 * Save or update the storage inside a quote.
	 * 
	 * @param entity
	 *            The storage entity to update.
	 * @param vo
	 *            The new quote storage data to persist.
	 * @return The formal entity.
	 */
	private UpdatedCost saveOrUpdate(final ProvQuoteStorage entity, final QuoteStorageEditionVo vo) {
		DescribedBean.copy(vo, entity);

		// Check the associations
		final ProvQuote quote = getQuoteFromSubscription(vo.getSubscription());
		entity.setConfiguration(quote);
		entity.setLocation(findLocation(vo.getSubscription(), vo.getLocation()));
		final String node = quote.getSubscription().getNode().getRefined().getId();
		entity.setPrice(findStorage(vo.getSubscription(), vo.getType(), vo.getLocation(), quote));
		entity.setQuoteInstance(Optional.ofNullable(vo.getQuoteInstance()).map(i -> findConfigured(qiRepository, i)).map(i -> {
			checkVisibility(i.getPrice().getType(), node);
			return i;
		}).orElse(null));

		// Check the storage compatibility with the instance
		final ProvStorageType type = entity.getPrice().getType();
		if (!type.isInstanceCompatible() && entity.getQuoteInstance() != null) {
			// The related storage type does not accept to be attached to an instance
			throw new ValidationJsonException("storage", "not-compatible-storage-instance", entity.getName(),
					entity.getQuoteInstance().getName());
		}

		// Check the storage limits
		if (type.getMaximal() != null && vo.getSize() > type.getMaximal()) {
			// The related storage type does not accept this value
			throw new ValidationJsonException("size", "Max", type.getMaximal());
		}
		entity.setSize(vo.getSize());

		// Save and update the costs
		final UpdatedCost cost = newUpdateCost(qsRepository, entity, this::updateCost);
		Optional.ofNullable(entity.getQuoteInstance())
				.ifPresent(q -> cost.setRelatedCosts(Collections.singletonMap(q.getId(), updateCost(q))));
		return cost;
	}

	/**
	 * Delete an instance from a quote. The total cost is updated.
	 * 
	 * @param id
	 *            The {@link ProvQuoteInstance}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("instance/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost deleteInstance(@PathParam("id") final int id) {
		// Delete the instance and also the attached storage
		return deleteAndUpdateCost(qiRepository, id, i -> {
			// Delete the relate storages
			i.getStorages().forEach(s -> deleteAndUpdateCost(qsRepository, s.getId(), Function.identity()::apply));

			// Decrement the unbound counter
			final ProvQuote configuration = i.getConfiguration();
			configuration.setUnboundCostCounter(configuration.getUnboundCostCounter() - BooleanUtils.toInteger(i.isUnboundCost()));
		});
	}

	/**
	 * Delete a storage from a quote. The total cost is updated.
	 * 
	 * @param id
	 *            The {@link ProvQuoteStorage}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("storage/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost deleteStorage(@PathParam("id") final int id) {
		return deleteAndUpdateCost(qsRepository, id, Function.identity()::apply);
	}

	/**
	 * Delete a configured entity and update the total cost of the associated quote.
	 */
	private <T extends AbstractQuoteResource> FloatingCost deleteAndUpdateCost(final RestRepository<T, Integer> repository,
			final Integer id, final Consumer<T> callback) {
		// Check the entity exists and is visible
		final T entity = super.findConfigured(repository, id);

		// Remove the cost of this entity
		addCost(entity, e -> {
			e.setCost(0d);
			e.setMaxCost(0d);
			return new FloatingCost(0);
		});

		// Callback before the deletion
		callback.accept(entity);

		// Delete the entity
		repository.deleteById(id);
		return toFloatingCost(entity.getConfiguration());

	}

	/**
	 * Request a cost update of the given entity and report the delta to the the
	 * global cost. The changes are persisted.
	 */
	private <T extends Costed> UpdatedCost newUpdateCost(final RestRepository<T, Integer> repository, final T entity,
			final Function<T, FloatingCost> costUpdater) {

		// Update the total cost, applying the delta cost
		final FloatingCost floatingCost = addCost(entity, costUpdater);
		repository.saveAndFlush(entity);

		final UpdatedCost cost = new UpdatedCost();
		cost.setId(entity.getId());
		cost.setResourceCost(floatingCost);
		cost.setTotalCost(toFloatingCost(entity.getConfiguration()));
		return cost;

	}

	/**
	 * Create the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances
	 *            from the associated provider.
	 * @param cpu
	 *            The amount of required CPU. Default is 1.
	 * @param ram
	 *            The amount of required RAM, in MB. Default is 1.
	 * @param constant
	 *            Optional constant CPU. When <code>false</code>, variable CPU is
	 *            requested. When <code>true</code> constant CPU is requested.
	 * @param os
	 *            The requested OS, default is "LINUX".
	 * @param type
	 *            Optional instance type identifier. May be <code>null</code>.
	 * @param term
	 *            Optional price term identifier. May be <code>null</code>.
	 * @param ephemeral
	 *            Optional ephemeral constraint. When <code>false</code> (default),
	 *            only non ephemeral instance are accepted. Otherwise
	 *            (<code>true</code>), ephemeral instance contract is accepted.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @return The lowest price instance configurations matching to the required
	 *         parameters. May be a template or a custom instance type.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteInstanceLookup lookupInstance(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("cpu") final double cpu, @DefaultValue(value = "1") @QueryParam("ram") final int ram,
			@QueryParam("constant") final Boolean constant, @DefaultValue(value = "LINUX") @QueryParam("os") final VmOs os,
			@QueryParam("type") final Integer type, @QueryParam("term") final Integer term,
			@QueryParam("ephemeral") final boolean ephemeral, @QueryParam("location") final String location) {
		// Check the security on this subscription
		return lookupInstance(getQuoteFromSubscription(subscription), cpu, ram, constant, os, type, term, ephemeral, location);
	}

	private QuoteInstanceLookup lookupInstance(final ProvQuote configuration, final double cpu, final int ram, final Boolean constant,
			final VmOs os, final Integer type, final Integer term, final boolean ephemeral, final String location) {
		// Get the attached node and check the security on this subscription
		final String node = configuration.getSubscription().getNode().getId();

		// Return only the first matching instance
		final VmOs pricingOs = Optional.ofNullable(os).map(VmOs::toPricingOs).orElse(null);
		final String resolvedLocation = Optional.ofNullable(location).orElseGet(() -> configuration.getLocation().getName());

		// Template instance
		final QuoteInstanceLookup template = ipRepository
				.findLowestPrice(node, cpu, ram, constant, pricingOs, term, type, ephemeral, resolvedLocation, PageRequest.of(0, 1))
				.stream().findFirst().map(ip -> newComputedInstancePrice(ip, toMonthly(ip.getCost()))).orElse(null);

		// Custom instance
		final QuoteInstanceLookup custom = ipRepository
				.findLowestCustomPrice(node, constant, pricingOs, term, resolvedLocation, PageRequest.of(0, 1)).stream().findFirst()
				.map(ip -> newComputedInstancePrice(ip, toMonthly(getComputeCustomCost(cpu, ram, ip)))).orElse(null);

		// Select the best instance
		if (template == null) {
			return custom;
		}
		if (custom == null) {
			return template;
		}
		return custom.getCost() < template.getCost() ? custom : template;
	}

	/**
	 * Return the instance price type available for a subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances
	 *            from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available price types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findInstancePriceTerm(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, iptRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
	}

	/**
	 * Return the instance types inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances
	 *            from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid instance types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstanceType> findInstance(@PathParam("subscription") final int subscription, @Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, instanceRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
	}

	/**
	 * Return the storage types the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the storages
	 *            from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/storage-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvStorageType> findStorageType(@PathParam("subscription") final int subscription, @Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, stRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
	}

	/**
	 * Return the available storage types from the provider linked to the given
	 * subscription..
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the storage
	 *            types from the associated provider.
	 * @param size
	 *            The requested size in GB.
	 * @param frequency
	 *            The optional requested {@link ProvStorageFrequency}.
	 * @param instance
	 *            The optional requested instance to be associated.
	 * @param optimized
	 *            The optional requested {@link ProvStorageOptimized}.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/storage-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<QuoteStorageLoopup> lookupStorage(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("size") final int size, @QueryParam("frequency") final ProvStorageFrequency frequency,
			@QueryParam("instance") final Integer instance, @QueryParam("optimized") final ProvStorageOptimized optimized,
			@QueryParam("location") final String location) {

		// Check the security on this subscription
		return lookupStorage(getQuoteFromSubscription(subscription), size, frequency, instance, optimized, location);
	}

	private List<QuoteStorageLoopup> lookupStorage(final ProvQuote configuration, final int size, final ProvStorageFrequency frequency,
			final Integer instance, final ProvStorageOptimized optimized, final String location) {

		// Get the attached node and check the security on this subscription
		final String node = configuration.getSubscription().getNode().getId();

		// The the right location from instance first
		final String resolvedLocation = Optional.ofNullable(location).orElseGet(() -> configuration.getLocation().getName());

		return spRepository.findLowestPrice(node, size, frequency, instance, optimized, resolvedLocation, PageRequest.of(0, 10)).stream()
				.map(spx -> (ProvStoragePrice) spx[0]).map(sp -> newComputedStoragePrice(sp, size, getStorageCost(sp, size)))
				.collect(Collectors.toList());
	}

	/**
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and
	 * computed price.
	 */
	private QuoteInstanceLookup newComputedInstancePrice(final ProvInstancePrice ip, final double cost) {
		final QuoteInstanceLookup result = new QuoteInstanceLookup();
		result.setCost(cost);
		result.setPrice(ip);
		return result;
	}

	/**
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and
	 * computed price.
	 */
	private QuoteStorageLoopup newComputedStoragePrice(final ProvStoragePrice sp, final int size, final double cost) {
		final QuoteStorageLoopup result = new QuoteStorageLoopup();
		result.setCost(cost);
		result.setPrice(sp);
		result.setSize(size);
		return result;
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
		entity.setLocation(findLocation(subscription, quote.getLocation()));
		return refreshCostAndResource(entity);
	}

	/**
	 * Compute the total cost and save it into the related quote. All separated
	 * compute and storage costs are also updated.
	 * 
	 * @param subscription
	 *            The subscription to compute
	 * @return The updated computed cost.
	 */
	@PUT
	@Path("{subscription:\\d+}/refresh-cost")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost refreshCost(@PathParam("subscription") final int subscription) {
		final ProvQuote entity = repository.getCompute(subscription);

		// Reset the costs to 0, will be updated further in this process
		entity.setCost(0d);
		entity.setMaxCost(0d);

		// Add the compute cost, and update the unbound cost
		entity.setUnboundCostCounter((int) entity.getInstances().stream().map(this::updateCost).map(fc -> addCost(entity, fc))
				.filter(FloatingCost::isUnbound).count());

		// Add the storage cost
		repository.getStorage(subscription).stream().map(this::updateCost).forEach(fc -> addCost(entity, fc));
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
		quote.setUnboundCostCounter((int) quote.getInstances().stream().map(this::refreshInstance).map(fc -> addCost(quote, fc))
				.filter(FloatingCost::isUnbound).count());

		// Update the storage, and add the cost
		repository.getStorage(quote.getSubscription().getId()).stream().map(this::refreshStorage).forEach(fc -> addCost(quote, fc));

		// Compute the the total cost
		return refreshCost(quote.getSubscription().getId());
	}

	private FloatingCost addCost(final ProvQuote entity, final FloatingCost fc) {
		entity.setCost(round(entity.getCost() + fc.getMin()));
		entity.setMaxCost(round(entity.getMaxCost() + fc.getMax()));
		return fc;
	}

	/**
	 * Round a cost to eliminate floating point artifact, and without required
	 * {@link BigDecimal} usage (not yet)
	 */
	private double round(double value) {
		return Math.round(value * 1000d) / 1000d;
	}

	/**
	 * Compute the monthly cost from hourly costs.
	 */
	private double toMonthly(double cost) {
		return Math.round(cost * 1000 * HOURS_BY_MONTH) / 1000d;
	}

	/**
	 * Update the actual monthly cost of given instance.
	 * 
	 * @param qi
	 *            The {@link ProvQuoteInstance} to update cost.
	 * @return The new cost.
	 */
	private FloatingCost updateCost(final ProvQuoteInstance qi) {
		return updateCost(qi, this::getComputeCost, this::toMonthly);
	}

	/**
	 * Update the actual monthly cost of given storage.
	 * 
	 * @param qs
	 *            The {@link ProvQuoteStorage} to update cost.
	 * @return The new cost.
	 */
	private FloatingCost updateCost(final ProvQuoteStorage qs) {
		return updateCost(qs, this::getStorageCost, Function.identity());
	}

	/**
	 * Update the actual monthly cost of given resource.
	 * 
	 * @param qr
	 *            The {@link AbstractQuoteResource} to update cost.
	 * @return The new cost.
	 */
	private <T extends AbstractQuoteResource> FloatingCost updateCost(final T qr, Function<T, FloatingCost> costProvider,
			Function<Double, Double> toMonthly) {
		final FloatingCost cost = costProvider.apply(qr);
		qr.setCost(toMonthly.apply(cost.getMin()));
		qr.setMaxCost(toMonthly.apply(cost.getMax()));
		return new FloatingCost(qr.getCost(), qr.getMaxCost(), qr.isUnboundCost());
	}

	/**
	 * Compute the hourly cost of a quote instance.
	 * 
	 * @param qi
	 *            The quote to evaluate.
	 * @return The cost of this instance.
	 */
	private FloatingCost getComputeCost(final ProvQuoteInstance qi) {
		// Fixed price + custom price
		final ProvInstancePrice ip = qi.getPrice();
		return computeFloat(ip.getCost() + (ip.getType().isCustom() ? getComputeCustomCost(qi.getCpu(), qi.getRam(), ip) : 0), qi);
	}

	/**
	 * Compute the hourly cost of a custom requested resource.
	 * 
	 * @param cpu
	 *            The requested CPU.
	 * @param ram
	 *            The requested RAM.
	 * @param ip
	 *            The instance price configuration.
	 * @return The cost of this custom instance.
	 */
	private double getComputeCustomCost(final Double cpu, final Integer ram, final ProvInstancePrice ip) {
		// Compute the count of the requested resources
		return getComputeCustomCost(cpu, ip.getCostCpu(), 1) + getComputeCustomCost(ram, ip.getCostRam(), 1024);
	}

	/**
	 * Compute the hourly cost of a custom requested resource.
	 * 
	 * @param requested
	 *            The request resource amount.
	 * @param cost
	 *            The cost of one resource.
	 * @param weight
	 *            The weight of one resource.
	 * @return The cost of this custom instance.
	 */
	private double getComputeCustomCost(Number requested, Double cost, final double weight) {
		// Compute the count of the requested resources
		return Math.ceil(requested.doubleValue() / weight) * cost;
	}

	/**
	 * Compute the monthly cost of a quote storage. The minimal quantity of related
	 * instance is considered.
	 * 
	 * @param quoteStorage
	 *            The quote to evaluate.
	 * @return The cost of this storage.
	 */
	private FloatingCost getStorageCost(final ProvQuoteStorage quoteStorage) {
		final double base = getStorageCost(quoteStorage.getPrice(), quoteStorage.getSize());
		return Optional.ofNullable(quoteStorage.getQuoteInstance()).map(i -> computeFloat(base, i)).orElseGet(() -> new FloatingCost(base));
	}

	/**
	 * Compute the cost using minimal and maximal quantity of related instance. no
	 * rounding there.
	 */
	private FloatingCost computeFloat(final double base, final ProvQuoteInstance instance) {
		final FloatingCost cost = new FloatingCost();
		cost.setMin(base * instance.getMinQuantity());
		cost.setMax(Optional.ofNullable(instance.getMaxQuantity()).orElse(instance.getMinQuantity()) * base);
		cost.setUnbound(instance.isUnboundCost());
		return cost;
	}

	/**
	 * Compute the cost of a storage.
	 * 
	 * @param storagePrice
	 *            The storage to evaluate.
	 * @param size
	 *            The requested size in GB.
	 * @return The cost of this storage.
	 */
	private double getStorageCost(final ProvStoragePrice storagePrice, final int size) {
		return round(Math.max(size, storagePrice.getType().getMinimal()) * storagePrice.getCostGb() + storagePrice.getCost());
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

	/**
	 * Check column's name validity
	 */
	private void checkHeaders(final String[] expected, final String... columns) {
		for (final String column : columns) {
			if (!ArrayUtils.contains(expected, column.trim())) {
				throw new BusinessException("Invalid header", column);
			}
		}
	}

	/**
	 * Upload a file of quote in add mode.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the locations
	 *            from the associated provider.
	 * @param uploadedFile
	 *            Instance entries files to import. Currently support only CSV
	 *            format.
	 * @param columns
	 *            the CSV header names.
	 * @param ramMultiplier
	 *            The multiplier for imported RAM values. Default is 1.
	 * @param term
	 *            The default {@link ProvInstancePriceTerm} used when no one is
	 *            defined in the CSV line
	 * @param encoding
	 *            CSV encoding. Default is UTF-8. TODO Add location column
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("{subscription:\\d+}/upload")
	public void upload(@PathParam("subscription") final int subscription, @Multipart(value = "csv-file") final InputStream uploadedFile,
			@Multipart(value = "columns", required = false) final String[] columns,
			@Multipart(value = "term", required = false) final String term,
			@Multipart(value = "memoryUnit", required = false) final Integer ramMultiplier,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		subscriptionResource.checkVisibleSubscription(subscription).getNode().getId();
		final Integer priceTypeEntity = Optional.ofNullable(iptRepository.findByName(subscription, term)).map(ProvInstancePriceTerm::getId)
				.orElse(null);

		// Check column's name validity
		final String[] sanitizeColumns = ArrayUtils.isEmpty(columns) ? DEFAULT_COLUMNS : columns;
		checkHeaders(ACCEPTED_COLUMNS, sanitizeColumns);

		// Build CSV header from array
		final String csvHeaders = StringUtils.chop(ArrayUtils.toString(sanitizeColumns)).substring(1).replace(',', ';') + "\n";

		// Build entries
		final String safeEncoding = ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name());
		csvForBean
				.toBean(InstanceUpload.class, new InputStreamReader(
						new SequenceInputStream(new ByteArrayInputStream(csvHeaders.getBytes(safeEncoding)), uploadedFile), safeEncoding))
				.stream().filter(Objects::nonNull).forEach(i -> persist(i, subscription, priceTypeEntity, ramMultiplier));
	}

	private void persist(final InstanceUpload upload, final int subscription, final Integer defaultType, final Integer ramMultiplier) {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setCpu(round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		vo.setEphemeral(upload.isEphemeral());
		vo.setInternet(upload.getInternet());
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setMaxQuantity(Optional.ofNullable(upload.getMaxQuantity()).map(q -> q <= 0 ? null : q).orElse(null));
		vo.setMinQuantity(upload.getMinQuantity());
		vo.setName(upload.getName());
		vo.setRam(ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		vo.setSubscription(subscription);

		// Instance selection
		final Integer instance = Optional.ofNullable(instanceRepository.findByName(subscription, upload.getType()))
				.map(ProvInstanceType::getId).orElse(null);
		final Integer term = Optional.ofNullable(iptRepository.findByName(subscription, upload.getTerm())).map(ProvInstancePriceTerm::getId)
				.orElse(defaultType);

		// Find the lowest price
		final ProvInstancePrice instancePrice = validateLookup("instance", lookupInstance(subscription, vo.getCpu(), vo.getRam(),
				upload.getConstant(), upload.getOs(), instance, term, upload.isEphemeral(), upload.getLocation()), upload.getName());

		vo.setPrice(instancePrice.getId());
		final UpdatedCost newInstance = createInstance(vo);
		final int qi = newInstance.getId();

		// Storage part
		final Integer size = Optional.ofNullable(upload.getDisk()).map(Double::intValue).orElse(0);
		if (size > 0) {
			// Size is provided
			final QuoteStorageEditionVo svo = new QuoteStorageEditionVo();

			// Default the storage frequency to HOT when not specified
			final ProvStorageFrequency frequency = ObjectUtils.defaultIfNull(upload.getFrequency(), ProvStorageFrequency.HOT);

			// Find the nicest storage
			svo.setType(lookupStorage(subscription, size, frequency, instancePrice.getType().getId(), upload.getOptimized(),
					upload.getLocation()).stream().findFirst().orElseThrow(() -> new ValidationJsonException("storage", "NotNull"))
							.getPrice().getType().getName());

			// Default the storage name to the instance name
			svo.setName(vo.getName());
			svo.setQuoteInstance(qi);
			svo.setSize(size);
			svo.setSubscription(subscription);
			createStorage(svo);
		}

	}

	@Override
	public void delete(int subscription, boolean remoteData) {
		repository.delete(repository.findBy("subscription.id", subscription));
	}

}
