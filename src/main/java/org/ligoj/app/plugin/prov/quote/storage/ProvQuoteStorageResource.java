/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.storage;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteResource;
import org.ligoj.app.plugin.prov.AbstractProvQuoteVmResource;
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteContainerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteFunctionRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.QuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * The storage part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteStorageResource
		extends AbstractProvQuoteResource<ProvStorageType, ProvStoragePrice, ProvQuoteStorage, QuoteStorageEditionVo> {

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvQuoteContainerRepository qcRepository;

	@Autowired
	private ProvQuoteFunctionRepository qfRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Override
	@POST
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(new ProvQuoteStorage(), vo);
	}

	@Override
	@PUT
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qsRepository, vo.getId()), vo);
	}

	@Override
	public Floating refresh(final ProvQuoteStorage qs) {
		final var quote = qs.getConfiguration();

		// Find the lowest price
		qs.setPrice(validateLookup("storage", lookup(quote, qs).stream().findFirst().orElse(null), qs.getName()));
		return updateCost(qs);
	}

	/**
	 * Check and return the storage price matching to the requirements and related name.
	 */
	private ProvStoragePrice findByTypeCode(final int subscription, final String code, final ProvLocation location,
			final ProvQuote quote) {
		return assertFound(spRepository.findByTypeCode(subscription, code,
				Optional.ofNullable(location).orElse(quote.getLocation()).getId()), code);
	}

	/**
	 * Save or update the storage inside a quote.
	 *
	 * @param entity The storage entity to update.
	 * @param vo     The new quote storage data to persist.
	 * @return The formal entity.
	 */
	private UpdatedCost saveOrUpdate(final ProvQuoteStorage entity, final QuoteStorageEditionVo vo) {
		checkInstance(vo);
		DescribedBean.copy(vo, entity);

		// Check the associations
		final int subscription = vo.getSubscription();
		final var quote = getQuoteFromSubscription(subscription);
		final var node = quote.getSubscription().getNode().getRefined().getId();
		entity.setConfiguration(quote);
		entity.setLocation(resource.findLocation(node, vo.getLocation()));
		entity.setLatency(vo.getLatency());
		entity.setOptimized(vo.getOptimized());
		entity.setSize(vo.getSize());
		entity.setQuantity(ObjectUtils.defaultIfNull(vo.getQuantity(), 1));
		entity.setSizeMax(vo.getSizeMax());
		entity.setQuoteInstance(checkInstance(subscription, vo.getInstance()));
		entity.setQuoteDatabase(checkDatabase(subscription, vo.getDatabase()));
		entity.setQuoteContainer(checkContainer(subscription, vo.getContainer()));
		entity.setQuoteFunction(checkFunction(subscription, vo.getFunction()));
		final var resolvedLocation = Objects.requireNonNullElseGet(entity.getLocation(),
				() -> Objects.requireNonNullElse(entity.getQuoteResource(), entity).getResolvedLocation());
		entity.setPrice(findByTypeCode(subscription, vo.getType(), resolvedLocation, quote));

		// Check the storage requirements to validate the linked price
		final var type = entity.getPrice().getType();
		if (lookup(quote, entity).stream().map(qs -> qs.getPrice().getType()).noneMatch(type::equals)) {
			// The related storage type does not match these requirements
			throw new ValidationJsonException("type", "type-incompatible-requirements", type.getCode());
		}

		// Save and update the costs
		final var cost = refreshCost(entity);
		Optional.ofNullable(entity.getQuoteResource()).ifPresent(qi -> cost.getRelated().put(qi.getResourceType(),
				Collections.singletonMap(qi.getId(), qi.toFloating())));

		// Add tags
		super.saveOrUpdate(entity, vo);

		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Refresh the cost of given storage and return the delta.
	 *
	 * @param entity The entity to refresh.
	 * @return The updated cost.
	 */
	public UpdatedCost refreshCost(final ProvQuoteStorage entity) {
		return newUpdateCost(qsRepository, entity, this::updateCost);
	}

	/**
	 * Check there is no ambiguous database/instance usage.
	 */
	private void checkInstance(final QuoteStorageEditionVo vo) {
		Stream.of(vo.getInstance(), vo.getDatabase(), vo.getContainer(), vo.getFunction()).filter(Objects::nonNull)
				.skip(1).findFirst().ifPresent(id -> {
					throw new ValidationJsonException("instance", "ambiguous-instance-database-container-function", id);
				});
	}

	/**
	 * Check the related quote instance exists and is related to the given node.
	 */
	private ProvQuoteInstance checkInstance(final int subscription, final Integer qi) {
		return qi == null ? null : resource.findConfigured(qiRepository, qi, subscription);
	}

	/**
	 * Check the related quote database exists and is related to the given node.
	 */
	private ProvQuoteDatabase checkDatabase(final int subscription, final Integer qb) {
		return qb == null ? null : resource.findConfigured(qbRepository, qb, subscription);
	}

	/**
	 * Check the related quote container exists and is related to the given node.
	 */
	private ProvQuoteContainer checkContainer(final int subscription, final Integer qc) {
		return qc == null ? null : resource.findConfigured(qcRepository, qc, subscription);
	}

	/**
	 * Check the related quote function exists and is related to the given node.
	 */
	private ProvQuoteFunction checkFunction(final int subscription, final Integer qf) {
		return qf == null ? null : resource.findConfigured(qfRepository, qf, subscription);
	}

	/**
	 * Delete all storages from a quote. The total cost is updated.
	 *
	 * @param subscription The related subscription.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("{subscription:\\d+}/storage")
	@Consumes(MediaType.APPLICATION_JSON)
	@Override
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	/**
	 * Delete a storage from a quote. The total cost is updated.
	 *
	 * @param id The {@link ProvQuoteStorage}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("storage/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Override
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	/**
	 * Return the storage types the instance inside a quote.
	 *
	 * @param subscription The subscription identifier, will be used to filter the storages from the associated
	 *                     provider.
	 * @param uriInfo      filter data.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/storage-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvStorageType> findType(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				stRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo).toUpperCase(),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the available storage types from the provider linked to the given subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the storage types from the associated
	 *                     provider.
	 * @param query        The storage requirements.
	 * @return The valid storage types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/storage-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<QuoteStorageLookup> lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteStorageQuery query) {

		// Check the security on this subscription
		return lookup(getQuoteFromSubscription(subscription), query);
	}

	/**
	 * Lookup from an existing entity.
	 */
	private List<QuoteStorageLookup> lookup(final ProvQuote quote, final ProvQuoteStorage qs) {
		return lookup(quote, qs, qs.getQuoteInstance(), qs.getQuoteDatabase(), qs.getQuoteContainer(),
				qs.getQuoteFunction());
	}

	/**
	 * Return the available storage types from the provider linked to the given quote.
	 *
	 * @param configuration The related quote.
	 * @param query         The storage requirements.
	 * @return The valid storage types for the given subscription.
	 */
	public List<QuoteStorageLookup> lookup(final ProvQuote configuration, final QuoteStorageQuery query) {
		final var qi = checkInstance(configuration.getSubscription().getId(), query.getInstance());
		final var qb = checkDatabase(configuration.getSubscription().getId(), query.getDatabase());
		final var qc = checkContainer(configuration.getSubscription().getId(), query.getContainer());
		final var qf = checkFunction(configuration.getSubscription().getId(), query.getFunction());
		return lookup(configuration, query, qi, qb, qc, qf);
	}

	private int getLocation(final String node, final QuoteStorage query, final int defaultLocation) {
		return query.getLocationName() == null ? defaultLocation
				: normalize(locationRepository.toId(node, query.getLocationName()));
	}

	private List<QuoteStorageLookup> lookup(final ProvQuote configuration, final QuoteStorage query,
			final ProvQuoteInstance qi, final ProvQuoteDatabase qb, final ProvQuoteContainer qc,
			final ProvQuoteFunction qf) {

		// Get the attached node and check the security on this subscription
		final var node = configuration.getSubscription().getNode().getRefined().getId();

		// The right location from instance first, then the request one
		final var attachment = Stream.of(qi, qb, qc, qf).filter(Objects::nonNull).findFirst().orElse(null);
		final int qLoc;
		final int qsLoc;
		if (attachment == null) {
			qLoc = 0;
			qsLoc = getLocation(node, query, configuration.getLocation().getId());
		} else {
			if (attachment.getLocation() == null) {
				qLoc = configuration.getLocation().getId();
			} else {
				qLoc = attachment.getLocation().getId();
			}
			qsLoc = getLocation(node, query, qLoc);
		}
		return spRepository
				.findLowestPrice(node, query.getSize(), normalize(query.getLatency()), normalize(qi), normalize(qb),
						qb == null ? "" : normalize(qb.getPrice().getStorageEngine()), normalize(qc), normalize(qf),
						query.getOptimized(), qsLoc, qLoc, PageRequest.of(0, 10))
				.stream().map(spx -> (ProvStoragePrice) spx[0])
				.map(sp -> newPrice(sp, query.getSize(), getCost(sp, query.getSize()))).toList();
	}

	/**
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and computed price.
	 */
	private QuoteStorageLookup newPrice(final ProvStoragePrice sp, final int size, final double cost) {
		final var result = new QuoteStorageLookup();
		result.setCost(cost);
		result.setPrice(sp);
		result.setSize(size);
		return result;
	}

	@Override
	protected Floating getCost(final ProvQuoteStorage qs) {
		// Sample Instance1, minQuantity=1, maxQuantity=5
		// qs1: quantity=4, price=5$ -> displayed quantity=[4,20], price=[20$-100$]
		// qs2: quantity=3, price=10$ -> displayed quantity=[3,15], price=[30$-150$]
		final var quantity = (double) ObjectUtils.defaultIfNull(qs.getQuantity(), 1);
		final var base = getCost(qs.getPrice(), qs.getSize()) * quantity;
		final var baseCo2 = getCo2(qs.getPrice(), qs.getSize()) * quantity;
		return Optional.ofNullable(qs.getQuoteResource())
				.map(qr -> AbstractProvQuoteVmResource.computeFloat(base, baseCo2, 0d, qr))
				.orElseGet(() -> new Floating(base, baseCo2));
	}

	/**
	 * Compute the cost of a storage.
	 *
	 * @param storagePrice The storage to evaluate.
	 * @param size         The requested size in GB.
	 * @return The cost of this storage.
	 */
	private double getCost(final ProvStoragePrice storagePrice, final int size) {
		final double increment = ObjectUtils.defaultIfNull(storagePrice.getType().getIncrement(), 1d);
		return round(Math.ceil(round(Math.max(size, storagePrice.getType().getMinimal()) / increment)) * increment
				* storagePrice.getCostGb() + storagePrice.getCost());
	}

	/**
	 * Compute the CO2 consumption of a storage.
	 *
	 * @param storagePrice The storage to evaluate.
	 * @param size         The requested size in GB.
	 * @return The cost of this storage.
	 */
	private double getCo2(final ProvStoragePrice storagePrice, final int size) {
		final double increment = ObjectUtils.defaultIfNull(storagePrice.getType().getIncrement(), 1d);
		return round(Math.ceil(round(Math.max(size, storagePrice.getType().getMinimal()) / increment)) * increment * 0
				+ storagePrice.getCo2());
	}

	@Override
	protected ResourceType getType() {
		return ResourceType.STORAGE;
	}

	@Override
	public RestRepository<ProvStoragePrice, Integer> getIpRepository() {
		return spRepository;
	}

	@Override
	public BaseProvQuoteRepository<ProvQuoteStorage> getQiRepository() {
		return qsRepository;
	}

	@Override
	public BaseProvTypeRepository<ProvStorageType> getItRepository() {
		return stRepository;
	}
}
