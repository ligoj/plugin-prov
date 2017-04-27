package org.ligoj.app.plugin.prov;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.model.Configurable;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.dao.PaginationDao;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
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
public class ProvResource extends AbstractConfiguredServicePlugin<ProvQuote> {

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = BASE_URL + "/prov";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_KEY = SERVICE_URL.replace('/', ':').substring(1);

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStorageRepository storageRepository;

	@Autowired
	protected IamProvider[] iamProvider;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	private PaginationDao pagination;

	/**
	 * Average hours in one month.
	 */
	private static final double HOURS_BY_MONTH = 24 * 30.5;

	/**
	 * Ordered/mapped columns.
	 */
	public static final Map<String, String> ORM_COLUMNS = new HashMap<>();

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
		vo.setStorage(entity.getStorage());
		return vo;
	}

	@GET
	@Path("{subscription:\\d+}")
	@Override
	@Transactional
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public QuoteVo getConfiguration(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);
		final QuoteVo vo = new QuoteVo();
		final ProvQuote entity = repository.getCompute(subscription);
		DescribedBean.copy(entity, vo);
		vo.copyAuditData(entity, toUser());
		vo.setInstances(entity.getInstances());
		vo.setStorages(
				repository.getStorage(subscription).stream().map(this::toStorageVo).collect(Collectors.toList()));

		// Also copy the cost to remove the necessary to compute it at first
		// sight
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
	@PUT
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public int updateInstance(final QuoteInstanceEditionVo vo) {
		final ProvQuoteInstance entity = new ProvQuoteInstance();
		DescribedBean.copy(vo, entity);

		// Check the associations
		entity.setConfiguration(getQuoteFromSubscription(vo.getSubscription()));
		final String providerId = entity.getConfiguration().getSubscription().getNode().getRefined().getId();
		entity.setInstancePrice(ipRepository.findOneExpected(vo.getInstancePrice()));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		checkVisibility(entity.getInstancePrice().getInstance(), providerId);

		// Save and update the costs
		final ProvQuoteInstance instance = qiRepository.saveAndFlush(entity);
		updatedCost(vo.getSubscription());
		return instance.getId();
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
	public void deleteInstance(final int id) {
		deleteAndUpdateCost(qiRepository, id);
	}

	/**
	 * Create the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage.
	 * @return The created storage identifier.
	 */
	@POST
	@PUT
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public int updateStorage(final QuoteStorageEditionVo vo) {
		final ProvQuoteStorage entity = new ProvQuoteStorage();
		DescribedBean.copy(vo, entity);

		// Check the associations
		entity.setConfiguration(getQuoteFromSubscription(vo.getSubscription()));
		final String providerId = entity.getConfiguration().getSubscription().getNode().getRefined().getId();
		entity.setStorage(checkVisibility(storageRepository.findOneExpected(vo.getStorage()), providerId));
		entity.setQuoteInstance(
				Optional.ofNullable(vo.getQuoteInstance()).map(i -> findConfigured(qiRepository, i)).map(i -> {
					checkVisibility(i.getInstancePrice().getInstance(), providerId);
					return i;
				}).orElse(null));

		// Check the limits
		if (entity.getStorage().getMaximal() != null && vo.getSize() > entity.getStorage().getMaximal()) {
			// The related storage type does not accept this value
			throw new ValidationJsonException("size", "Max", entity.getStorage().getMaximal());
		}
		entity.setSize(vo.getSize());

		// Save and update the costs
		final ProvQuoteStorage storage = qsRepository.saveAndFlush(entity);
		updatedCost(vo.getSubscription());
		return storage.getId();
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
	public void deleteStorage(final int id) {
		deleteAndUpdateCost(qsRepository, id);
	}

	/**
	 * Delete a configured entity and update the total cost of the associated
	 * quote.
	 */
	private <T extends Configurable<ProvQuote, K>, K extends Serializable> void deleteAndUpdateCost(
			final RestRepository<T, K> repository, final K id) {
		final T entity = super.findConfigured(repository, id);
		final int subscription = entity.getConfiguration().getSubscription().getId();
		repository.delete(id);
		updatedCost(subscription);
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
	@Path("instance/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public LowestPrice findInstance(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("cpu") final double cpu,
			@DefaultValue(value = "1") @QueryParam("ram") final int ram,
			@DefaultValue(value = "false") @QueryParam("constant") final boolean constant,
			@DefaultValue(value = "LINUX") @QueryParam("os") final VmOs os, @QueryParam("type") final Integer type) {
		// Get the attached node and check the security on this subscription
		final String node = subscriptionResource.checkVisibleSubscription(subscription).getNode().getId();
		final LowestPrice price = new LowestPrice();

		// Return only the first matching instance
		price.setInstance(ipRepository.findLowestPrice(node, cpu, ram, constant, os, type, new PageRequest(0, 1))
				.stream().findFirst().map(ip -> newComputedInstancePrice(ip, toMonthly(ip.getCost()))).orElse(null));
		price.setCustom(ipRepository.findLowestCustomPrice(node, constant, os, type, new PageRequest(0, 1)).stream()
				.findFirst().map(ip -> newComputedInstancePrice(ip, toMonthly(getComputeCustomCost(cpu, ram, ip))))
				.orElse(null));
		return price;
	}

	/**
	 * Create the instance inside a quote.
	 * 
	 * @param subscription
	 *            The subscription identifier, will be used to filter the
	 *            instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid price types for the given subscription.
	 */
	@GET
	@Path("price-type/{subscription:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceType> findInstancePriceType(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo,
				pagination.<ProvInstancePriceType>findAll(ProvInstancePriceType.class,
						paginationJson.getUiPageRequest(uriInfo), ORM_COLUMNS),
				Function.identity());
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
	 * Return the quote status linked to given subscription.
	 * 
	 * @param subscription
	 *            The parent subscription identifier.
	 * @return The quote status (summary only) linked to given subscription.
	 */
	@Transactional
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public QuoteLigthVo getSusbcriptionStatus(final int subscription) {
		final QuoteLigthVo vo = new QuoteLigthVo();
		final Object[] compute = repository.getComputeSummary(subscription).get(0);
		final Object[] storage = repository.getStorageSummary(subscription).get(0);
		final ProvQuote entity = (ProvQuote) compute[0];
		DescribedBean.copy(entity, vo);
		vo.setCost(entity.getCost());
		vo.setNbInstances(((Long) compute[1]).intValue());
		vo.setTotalCpu((Double) compute[2] + (Double) compute[3]);
		vo.setTotalRam(((Long) compute[4]).intValue() + ((Long) compute[5]).intValue());
		vo.setNbStorages(((Long) storage[1]).intValue());
		vo.setTotalStorage(((Long) storage[2]).intValue());
		return vo;
	}

	/**
	 * Compute the total cost without transactional/snapshot costs and save it
	 * into the related quote.
	 * 
	 * @param subscription
	 *            The subscription to compute
	 * @return the computed cost without transactional/snapshot, support,...
	 *         costs.
	 */
	public double updatedCost(final int subscription) {
		final ProvQuote entity = repository.getCompute(subscription);
		final List<ProvQuoteStorage> storages = repository.getStorage(subscription);

		// Compute compute costs
		final double computeCost = entity.getInstances().stream().mapToDouble(this::getComputeCost).sum();
		final double storageCost = storages.stream().mapToDouble(this::getStorageCost).reduce(0.0, Double::sum);

		// Hour to monthly cost
		entity.setCost(toMonthly(computeCost) + Math.round(storageCost * 1000) / 1000d);
		return entity.getCost();
	}

	private double toMonthly(double cost) {
		return Math.round(cost * 1000) * HOURS_BY_MONTH / 1000d;
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
		return quoteInstance.getInstancePrice().getCost() + getComputeCustomCost(quoteInstance.getCpu(),
				quoteInstance.getRam(), quoteInstance.getInstancePrice());
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
		return required == null ? 0 : Math.ceil(required.doubleValue() / multiplicator) * cost;
	}

	/**
	 * Compute the cost of a quote storage.
	 * 
	 * @param quoteStorage
	 *            The quote to evaluate.
	 * @return The cost of this storage.
	 */
	private double getStorageCost(final ProvQuoteStorage quoteStorage) {
		return Math.max(quoteStorage.getSize(), quoteStorage.getStorage().getMinimal())
				* quoteStorage.getStorage().getCost();
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
}
