
package org.ligoj.app.plugin.prov;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.resource.OnNullReturn404;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Service;

/**
 * The provisioning service. There is complete quote configuration along the
 * subscription.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
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
	private static final String[] ACCEPTED_COLUMNS = { "name", "cpu", "ram", "constant", "os", "disk", "frequency",
			"optimized", "priceType", "instance" };

	/**
	 * Average hours in one month.
	 */
	private static final double HOURS_BY_MONTH = 24 * 30.5;

	/**
	 * Ordered/mapped columns.
	 */
	private static final Map<String, String> ORM_COLUMNS = new HashMap<>();

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private CsvForBean csvForBean;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvInstancePriceTypeRepository iptRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstanceRepository instanceRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

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

	/**
	 * Transform a {@link ProvQuoteStorage} to {@link QuoteStorageVo}
	 */
	private QuoteStorageVo toStorageVo(final ProvQuoteStorage entity) {
		final QuoteStorageVo vo = new QuoteStorageVo();
		DescribedBean.copy(entity, vo);
		vo.setId(entity.getId());
		vo.setQuoteInstance(Optional.ofNullable(entity.getQuoteInstance()).map(Persistable::getId).orElse(null));
		vo.setSize(entity.getSize());
		vo.setType(entity.getType());
		vo.setCost(entity.getCost());
		return vo;
	}

	@GET
	@Path("{subscription:\\d+}")
	@Override
	public QuoteVo getConfiguration(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);
		final QuoteVo vo = new QuoteVo();
		final ProvQuote entity = repository.getCompute(subscription);
		DescribedBean.copy(entity, vo);
		vo.copyAuditData(entity, toUser());
		vo.setInstances(entity.getInstances());
		vo.setStorages(
				repository.getStorage(subscription).stream().map(this::toStorageVo).collect(Collectors.toList()));
		// Also copy the cost even if computable
		vo.setCost(entity.getCost());

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
	 * @return The created instance identifier.
	 */
	@POST
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public int createInstance(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(new ProvQuoteInstance(), vo).getId();
	}

	/**
	 * Update the instance inside a quote.
	 * 
	 * @param vo
	 *            The quote instance.
	 * @return The created instance identifier.
	 */
	@PUT
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public void updateInstance(final QuoteInstanceEditionVo vo) {
		saveOrUpdate(findConfigured(qiRepository, vo.getId()), vo);
	}

	/**
	 * Save or update the given entity from the {@link QuoteInstanceEditionVo}.
	 * The computed cost are recursively updated from the instance to the quote
	 * total cost.
	 */
	private ProvQuoteInstance saveOrUpdate(final ProvQuoteInstance entity, final QuoteInstanceEditionVo vo) {
		DescribedBean.copy(vo, entity);

		// Check the associations
		entity.setConfiguration(getQuoteFromSubscription(vo.getSubscription()));
		final String providerId = entity.getConfiguration().getSubscription().getNode().getRefined().getId();
		entity.setInstancePrice(ipRepository.findOneExpected(vo.getInstancePrice()));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		checkVisibility(entity.getInstancePrice().getInstance(), providerId);

		// Update the total cost, applying the delta cost
		addCost(entity, this::updateCost);

		// Save and update the costs
		qiRepository.saveAndFlush(entity);
		return entity;
	}

	/**
	 * Add a cost to the quote related to given entity.
	 * 
	 * @param entity
	 *            The configured entity, related to a quote.
	 * @param addCost
	 *            The monthly cost to add, may positive or negative.
	 */
	private <T extends Costed> void addCost(final T entity, final Consumer<T> costUpdater) {
		// Update the cost of this instance, saving the previous one
		final double oldCost = ObjectUtils.defaultIfNull(entity.getCost(), 0d);

		// Process the update
		costUpdater.accept(entity);

		// Report the delta to the quote
		final double oldQuoteCost = ObjectUtils.defaultIfNull(entity.getConfiguration().getCost(), 0d);
		entity.getConfiguration().setCost(round(oldQuoteCost + entity.getCost() - oldCost));
	}

	/**
	 * Delete an instance from a quote. The total cost is updated.
	 * 
	 * @param id
	 *            The {@link ProvQuoteInstance}'s identifier to delete.
	 */
	@DELETE
	@Path("instance/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void deleteInstance(@PathParam("id") final int id) {
		// Delete the instance and also the attached storage
		deleteAndUpdateCost(qiRepository, id, i -> i.getStorages()
				.forEach(s -> deleteAndUpdateCost(qsRepository, s.getId(), Function.identity()::apply)));
	}

	/**
	 * Delete all instances from a quote. The total cost is updated.
	 * 
	 * @param subscription
	 *            The related subscription.
	 */
	@DELETE
	@Path("instance/reset/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void deleteAllInstances(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);

		// Delete all instance with cascaded delete for storages
		qiRepository.delete(qiRepository.findAllBy("configuration.subscription.id", subscription));

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of instances cost and related storage costs
		refreshCost(subscription);
	}

	/**
	 * Delete all storages from a quote. The total cost is updated.
	 * 
	 * @param subscription
	 *            The related subscription.
	 */
	@DELETE
	@Path("storage/reset/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void deleteAllStorages(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);

		// Delete all storages related to any instance, then the instances
		qsRepository.delete(qsRepository.findAllBy("configuration.subscription.id", subscription));

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of storage costs.
		refreshCost(subscription);
	}

	/**
	 * Create the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage.
	 * @return The created storage identifier.
	 */
	@POST
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public int createStorage(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(new ProvQuoteStorage(), vo).getId();
	}

	/**
	 * Update the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage.
	 * @return The created storage identifier.
	 */
	@PUT
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public void updateStorage(final QuoteStorageEditionVo vo) {
		saveOrUpdate(findConfigured(qsRepository, vo.getId()), vo);
	}

	/**
	 * Save or update the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage.
	 * @return The formal entity.
	 */
	private ProvQuoteStorage saveOrUpdate(final ProvQuoteStorage entity, final QuoteStorageEditionVo vo) {
		DescribedBean.copy(vo, entity);

		// Check the associations
		entity.setConfiguration(getQuoteFromSubscription(vo.getSubscription()));
		final String providerId = entity.getConfiguration().getSubscription().getNode().getRefined().getId();
		entity.setType(checkVisibility(stRepository.findOneExpected(vo.getType()), providerId));
		entity.setQuoteInstance(
				Optional.ofNullable(vo.getQuoteInstance()).map(i -> findConfigured(qiRepository, i)).map(i -> {
					checkVisibility(i.getInstancePrice().getInstance(), providerId);
					return i;
				}).orElse(null));

		// Check the storage limits
		if (entity.getType().getMaximal() != null && vo.getSize() > entity.getType().getMaximal()) {
			// The related storage type does not accept this value
			throw new ValidationJsonException("size", "Max", entity.getType().getMaximal());
		}
		entity.setSize(vo.getSize());

		// Update the total cost, applying the delta cost
		addCost(entity, this::updateCost);

		// Save and update the costs
		return qsRepository.saveAndFlush(entity);
	}

	/**
	 * Delete a storage from a quote. The total cost is updated.
	 * 
	 * @param id
	 *            The {@link ProvQuoteStorage}'s identifier to delete.
	 */
	@DELETE
	@Path("storage/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void deleteStorage(@PathParam("id") final int id) {
		deleteAndUpdateCost(qsRepository, id, Function.identity()::apply);
	}

	/**
	 * Delete a configured entity and update the total cost of the associated
	 * quote.
	 */
	private <T extends Costed> void deleteAndUpdateCost(final RestRepository<T, Integer> repository, final Integer id,
			final Consumer<T> callback) {
		// Check the entity exists and is visible
		final T entity = super.findConfigured(repository, id);

		// Remove the cost from the quote
		entity.getConfiguration().setCost(entity.getConfiguration().getCost() - entity.getCost());

		// Callback before the deletion
		callback.accept(entity);
		// Delete the entity
		repository.delete(id);
	}

	/**
	 * Create the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            instances from the associated provider.
	 * @param cpu
	 *            The amount of required CPU. Default is 1.
	 * @param ram
	 *            The amount of required RAM, in MB. Default is 1.
	 * @param constant
	 *            When true, the constant constraint is applied.
	 * @param os
	 *            The requested OS, default is "LINUX".
	 * @param type
	 *            The required price type identifier. May be <code>null</code>.
	 * @return The lowest price instance configurations matching to the required
	 *         parameters for standard instance (if available) and custom
	 *         instance (if available too) and also the lower instance based
	 *         price for a weaker requirement if applicable.
	 */
	@GET
	@Path("instance-lookup/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public LowestInstancePrice lookupInstance(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("cpu") final double cpu,
			@DefaultValue(value = "1") @QueryParam("ram") final int ram,
			@DefaultValue(value = "false") @QueryParam("constant") final boolean constant,
			@DefaultValue(value = "LINUX") @QueryParam("os") final VmOs os,
			@QueryParam("instance") final Integer instance, @QueryParam("price-type") final Integer type) {
		// Get the attached node and check the security on this subscription
		final String node = subscriptionResource.checkVisibleSubscription(subscription).getNode().getId();
		final LowestInstancePrice price = new LowestInstancePrice();

		// Return only the first matching instance
		price.setInstance(ipRepository
				.findLowestPrice(node, cpu, ram, constant, os, type, instance, new PageRequest(0, 1)).stream()
				.findFirst().map(ip -> newComputedInstancePrice(ip, toMonthly(ip.getCost()))).orElse(null));
		price.setCustom(ipRepository.findLowestCustomPrice(node, constant, os, type, new PageRequest(0, 1)).stream()
				.findFirst().map(ip -> newComputedInstancePrice(ip, toMonthly(getComputeCustomCost(cpu, ram, ip))))
				.orElse(null));
		return price;
	}

	/**
	 * Return the price type available for a subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available price types for the given subscription.
	 */
	@GET
	@Path("instance-price-type/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceType> findInstancePriceType(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, iptRepository.findAll(subscription,
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the instance types inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid instance types for the given subscription.
	 */
	@GET
	@Path("instance/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstance> findInstance(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, instanceRepository.findAll(subscription,
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the storage types the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            storages from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("storage-type/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvStorageType> findStorageType(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisibleSubscription(subscription);
		return paginationJson.applyPagination(uriInfo, stRepository.findAll(subscription,
				DataTableAttributes.getSearch(uriInfo), paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the storage types the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            storages from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("storage-lookup/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@OnNullReturn404
	public ComputedStoragePrice lookupStorage(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("size") final int size,
			@QueryParam("frequency") final ProvStorageFrequency frequency,
			@QueryParam("optimized") final ProvStorageOptimized optimized) {

		// Get the attached node and check the security on this subscription
		final String node = subscriptionResource.checkVisibleSubscription(subscription).getNode().getId();

		// Return only the first matching instance
		return stRepository.findLowestPrice(node, size, frequency, optimized, new PageRequest(0, 1)).stream()
				.findFirst().map(st -> newComputedStoragePrice(st, getStorageCost(st, size))).orElse(null);
	}

	/**
	 * Build a new {@link ComputedInstancePrice} from {@link ProvInstancePrice}
	 * and computed price.
	 */
	private ComputedInstancePrice newComputedInstancePrice(final ProvInstancePrice ip, final double cost) {
		final ComputedInstancePrice result = new ComputedInstancePrice();
		result.setCost(cost);
		result.setInstance(ip);
		return result;
	}

	/**
	 * Build a new {@link ComputedInstancePrice} from {@link ProvInstancePrice}
	 * and computed price.
	 */
	private ComputedStoragePrice newComputedStoragePrice(final ProvStorageType st, final double cost) {
		final ComputedStoragePrice result = new ComputedStoragePrice();
		result.setCost(cost);
		result.setType(st);
		return result;
	}

	/**
	 * Return the quote status linked to given subscription.
	 * 
	 * @param subscription
	 *            The parent subscription identifier.
	 * @return The quote status (summary only) linked to given subscription.
	 */
	@Transactional
	public QuoteLigthVo getSusbcriptionStatus(final int subscription) {
		final QuoteLigthVo vo = new QuoteLigthVo();
		final Object[] compute = repository.getComputeSummary(subscription).get(0);
		final Object[] storage = repository.getStorageSummary(subscription).get(0);
		final ProvQuote entity = (ProvQuote) compute[0];
		DescribedBean.copy(entity, vo);
		vo.setCost(round(entity.getCost()));
		vo.setNbInstances(((Long) compute[1]).intValue());
		vo.setTotalCpu((Double) compute[2]);
		vo.setTotalRam(((Long) compute[3]).intValue());
		vo.setNbStorages(((Long) storage[1]).intValue());
		vo.setTotalStorage(((Long) storage[2]).intValue());
		return vo;
	}

	/**
	 * Compute the total cost without transactional/snapshot costs and save it
	 * into the related quote. All compute and storage costs are updated.
	 * 
	 * @param subscription
	 *            The subscription to compute
	 * @return the computed cost without transactional/snapshot, support,...
	 *         costs.
	 */
	@PUT
	@Path("refresh")
	@Consumes(MediaType.APPLICATION_JSON)
	public double refreshCost(final int subscription) {
		final ProvQuote entity = repository.getCompute(subscription);
		final List<ProvQuoteStorage> storages = repository.getStorage(subscription);

		// Compute compute costs
		final double computeCost = entity.getInstances().stream().mapToDouble(this::updateCost).sum();
		final double storageCost = storages.stream().mapToDouble(this::updateCost).reduce(0.0, Double::sum);

		// Hour to monthly cost
		entity.setCost(round(computeCost + storageCost));
		return entity.getCost();
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
		return Math.round(cost * 1000) * HOURS_BY_MONTH / 1000d;
	}

	/**
	 * Update the actual monthly cost of given instance.
	 * 
	 * @param qi
	 *            The {@link ProvQuoteInstance} to update cost.
	 * @return The new cost.
	 */
	private Double updateCost(final ProvQuoteInstance qi) {
		qi.setCost(toMonthly(getComputeCost(qi)));
		return qi.getCost();
	}

	/**
	 * Update the actual monthly cost of given storage.
	 * 
	 * @param qi
	 *            The {@link ProvQuoteStorage} to update cost.
	 * @return The new cost.
	 */
	private Double updateCost(final ProvQuoteStorage qs) {
		qs.setCost(getStorageCost(qs));
		return qs.getCost();
	}

	/**
	 * Compute the cost of a quote instance.
	 * 
	 * @param quoteInstance
	 *            The quote to evaluate.
	 * @return The cost of this instance.
	 */
	private double getComputeCost(final ProvQuoteInstance quoteInstance) {
		// Fixed price + custom price
		return quoteInstance.getInstancePrice().getCost() + (quoteInstance.getInstancePrice().getInstance().isCustom()
				? getComputeCustomCost(quoteInstance.getCpu(), quoteInstance.getRam(), quoteInstance.getInstancePrice())
				: 0);
	}

	/**
	 * Compute the cost of a custom requested resource.
	 * 
	 * @param quoteInstance
	 *            The quote to evaluate.
	 * @param required
	 *            The request resource amount.
	 * @param cost
	 *            The cost of one resource.
	 * @return The cost of this custom instance.
	 */
	private double getComputeCustomCost(final Double cpu, final Integer ram, final ProvInstancePrice ip) {
		// Compute the count of the requested resources
		return getComputeCustomCost(cpu, ip.getCostCpu(), 1) + getComputeCustomCost(ram, ip.getCostRam(), 1024);
	}

	/**
	 * Compute the cost of a custom requested resource.
	 * 
	 * @param quoteInstance
	 *            The quote to evaluate.
	 * @param required
	 *            The request resource amount.
	 * @param cost
	 *            The cost of one resource.
	 * @return The cost of this custom instance.
	 */
	private double getComputeCustomCost(Number required, Double cost, final double multiplicator) {
		// Compute the count of the requested resources
		return Math.ceil(required.doubleValue() / multiplicator) * cost;
	}

	/**
	 * Compute the cost of a quote storage.
	 * 
	 * @param quoteStorage
	 *            The quote to evaluate.
	 * @return The cost of this storage.
	 */
	private double getStorageCost(final ProvQuoteStorage quoteStorage) {
		return getStorageCost(quoteStorage.getType(), quoteStorage.getSize());
	}

	/**
	 * Compute the cost of a storage.
	 * 
	 * @param storageType
	 *            The storage to evaluate.
	 * @param size
	 *            The requested size in GB.
	 * @return The cost of this storage.
	 */
	private double getStorageCost(final ProvStorageType storageType, final int size) {
		return Math.max(size, storageType.getMinimal()) * storageType.getCost();
	}

	@Override
	public void create(final int subscription) {
		// Add an empty quote
		final ProvQuote configuration = new ProvQuote();
		configuration.setSubscription(subscriptionRepository.findOne(subscription));
		configuration.setCost(0d);

		// Associate a default name and description
		configuration.setName(configuration.getSubscription().getProject().getName());
		configuration.setDescription(configuration.getSubscription().getProject().getPkey() + "-> "
				+ configuration.getSubscription().getNode().getName());
		repository.saveAndFlush(configuration);
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
	 * @param uploadedFile
	 *            Instance entries files to import. Currently support only CSV
	 *            format.
	 * @param ramMultiplier
	 *            The multiplier for imported RAM values. Default is 1.
	 * @param columns
	 *            the CSV header names.
	 * @param encoding
	 *            CSV encoding. Default is UTF-8.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("upload/{subscription:\\d+}")
	public void upload(@PathParam("subscription") final int subscription,
			@Multipart(value = "csv-file") final InputStream uploadedFile,
			@Multipart(value = "columns", required = false) final String[] columns,
			@Multipart(value = "priceType", required = false) final Integer defaultPriceType,
			@Multipart(value = "memoryUnit", required = false) final Integer ramMultiplier,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		subscriptionResource.checkVisibleSubscription(subscription).getNode().getId();
		final Integer priceTypeEntity = Optional.ofNullable(iptRepository.findById(subscription, defaultPriceType))
				.map(ProvInstancePriceType::getId).orElse(null);

		// Check column's name validity
		final String[] sanitizeColumns = ArrayUtils.isEmpty(columns) ? DEFAULT_COLUMNS : columns;
		checkHeaders(ACCEPTED_COLUMNS, sanitizeColumns);

		// Build CSV header from array
		final String csvHeaders = StringUtils.chop(ArrayUtils.toString(sanitizeColumns)).substring(1).replace(',', ';')
				+ "\n";

		// Build entries
		final String safeEncoding = ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name());
		csvForBean
				.toBean(InstanceUpload.class, new InputStreamReader(new SequenceInputStream(
						new ByteArrayInputStream(csvHeaders.getBytes(safeEncoding)), uploadedFile), safeEncoding))
				.stream().filter(Objects::nonNull)
				.forEach(i -> persist(i, subscription, priceTypeEntity, ramMultiplier));

	}

	private void persist(final InstanceUpload upload, final int subscription, final Integer defaultType,
			final Integer ramMultiplier) {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setSubscription(subscription);
		vo.setName(upload.getName());
		vo.setCpu(round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		vo.setRam(
				ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		final Boolean constant = ObjectUtils.defaultIfNull(upload.getConstant(), Boolean.FALSE);

		// Instance selection
		final Integer instance = Optional.ofNullable(instanceRepository.findByName(subscription, upload.getInstance()))
				.map(ProvInstance::getId).orElse(null);
		final Integer type = Optional.ofNullable(iptRepository.findByName(subscription, upload.getPriceType()))
				.map(ProvInstancePriceType::getId).orElse(defaultType);
		final LowestInstancePrice price = lookupInstance(subscription, vo.getCpu(), vo.getRam(), constant,
				upload.getOs(), instance, type);

		// Find the lowest price
		ComputedInstancePrice lowest = price.getInstance();
		if (price.getCustom() == null) {
			lowest = price.getInstance();
		} else if (price.getInstance() == null || price.getInstance().getCost() > price.getCustom().getCost()) {
			lowest = price.getCustom();
		}
		ValidationJsonException.assertNotnull(lowest, "instance");
		vo.setInstancePrice(lowest.getInstance().getId());
		final int qi = createInstance(vo);

		// Storage part
		final Integer size = Optional.ofNullable(upload.getDisk()).map(Double::intValue).orElse(0);
		if (size > 0) {
			// Size is provided
			final QuoteStorageEditionVo svo = new QuoteStorageEditionVo();

			// Default the storage frequency to HOT when not specified
			final ProvStorageFrequency frequency = ObjectUtils.defaultIfNull(upload.getFrequency(),
					ProvStorageFrequency.HOT);

			// Find the nicest storage
			ComputedStoragePrice storagePrice = lookupStorage(subscription, size, frequency, upload.getOptimized());
			ValidationJsonException.assertNotnull(storagePrice, "storage");

			// Default the storage name to the instance
			svo.setName(vo.getName());
			svo.setQuoteInstance(qi);
			svo.setSize(size);
			svo.setSubscription(subscription);
			svo.setType(storagePrice.getType().getId());
			createStorage(svo);
		}

	}

	@Override
	public void delete(int subscription, boolean remoteData) {
		repository.delete(repository.findBy("subscription.id", subscription));
	}
}
