/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.storage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.AbstractProvQuoteResource;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResourceInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.QuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Service;

/**
 * The storage part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteStorageResource
		extends AbstractProvQuoteResource<ProvStorageType, ProvStoragePrice, ProvQuoteStorage> {

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Override
	protected ProvQuoteStorageRepository getResourceRepository() {
		return qsRepository;
	}

	/**
	 * Create the storage inside a quote.
	 *
	 * @param vo The quote storage details.
	 * @return The created instance cost details with identifier.
	 */
	@POST
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(new ProvQuoteStorage(), vo);
	}

	/**
	 * Update the storage inside a quote.
	 *
	 * @param vo The quote storage update.
	 * @return The new cost configuration.
	 */
	@PUT
	@Path("storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteStorageEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qsRepository, vo.getId()), vo);
	}

	@Override
	public FloatingCost refresh(final ProvQuoteStorage qs) {
		final var quote = qs.getConfiguration();

		// Find the lowest price
		qs.setPrice(validateLookup("storage",
				lookup(quote, qs, qs.getQuoteInstance(), qs.getQuoteDatabase()).stream().findFirst().orElse(null),
				qs.getName()));
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
		entity.setPrice(findByTypeCode(subscription, vo.getType(), entity.getLocation(), quote));
		entity.setLatency(vo.getLatency());
		entity.setOptimized(vo.getOptimized());
		entity.setSize(vo.getSize());
		entity.setSizeMax(vo.getSizeMax());
		entity.setQuoteInstance(checkInstance(subscription, vo.getQuoteInstance()));
		entity.setQuoteDatabase(checkDatabase(subscription, vo.getQuoteDatabase()));

		// Check the storage requirements to validate the linked price
		final var type = entity.getPrice().getType();
		if (lookup(quote, entity, entity.getQuoteInstance(), entity.getQuoteDatabase()).stream()
				.map(qs -> qs.getPrice().getType()).noneMatch(type::equals)) {
			// The related storage type does not match these requirements
			throw new ValidationJsonException("type", "type-incompatible-requirements", type.getCode());
		}

		// Save and update the costs
		final var cost = refreshCost(entity);
		Optional.ofNullable(entity.getQuoteResource()).ifPresent(qi -> cost.getRelated().put(qi.getResourceType(),
				Collections.singletonMap(qi.getId(), qi.toFloatingCost())));

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
	 * Check the related quote instance exists and is related to the given node.
	 */
	private ProvQuoteInstance checkInstance(final int subscription, final Integer qi) {
		return qi == null ? null : resource.findConfigured(qiRepository, qi, subscription);
	}

	/**
	 * Check there is no ambiguous database/instance usage.
	 */
	private void checkInstance(final QuoteStorageEditionVo vo) {
		if (vo.getQuoteInstance() != null && vo.getQuoteDatabase() != null) {
			throw new ValidationJsonException("instance", "ambiguous-instance-database", vo.getQuoteInstance());
		}
	}

	/**
	 * Check the related quote database exists and is related to the given node.
	 */
	private ProvQuoteDatabase checkDatabase(final int subscription, final Integer qb) {
		return qb == null ? null : resource.findConfigured(qbRepository, qb, subscription);
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
				stRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
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
	 * Return the available storage types from the provider linked to the given quote.
	 *
	 * @param configuration The related quote.
	 * @param query         The storage requirements.
	 * @return The valid storage types for the given subscription.
	 */
	public List<QuoteStorageLookup> lookup(final ProvQuote configuration, final QuoteStorageQuery query) {
		final var qi = checkInstance(configuration.getSubscription().getId(), query.getInstance());
		final var qb = checkDatabase(configuration.getSubscription().getId(), query.getDatabase());
		return lookup(configuration, query, qi, qb);
	}

	private List<QuoteStorageLookup> lookup(final ProvQuote configuration, final QuoteStorage query,
			final ProvQuoteInstance qi, final ProvQuoteDatabase qb) {

		// Get the attached node and check the security on this subscription
		final var node = configuration.getSubscription().getNode().getRefined().getId();

		// The the right location from instance first, then the request one
		final int qLoc = configuration.getLocation().getId();
		final int qsLoc;
		if (query.getLocationName() == null) {
			qsLoc = Optional.ofNullable(qi == null ? qb : qi).map(AbstractQuoteResourceInstance::getLocation)
					.map(Persistable::getId).orElse(qLoc);
		} else {
			qsLoc = locationRepository.toId(node, query.getLocationName());
		}

		return spRepository
				.findLowestPrice(node, query.getSize(), query.getLatency(), query.getInstance(), query.getDatabase(),
						query.getOptimized(), qsLoc, qLoc, PageRequest.of(0, 10))
				.stream().map(spx -> (ProvStoragePrice) spx[0])
				.map(sp -> newPrice(sp, query.getSize(), getCost(sp, query.getSize()))).collect(Collectors.toList());
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
	protected FloatingCost getCost(final ProvQuoteStorage qs) {
		final var base = getCost(qs.getPrice(), qs.getSize());
		return Optional.ofNullable(qs.getQuoteResource())
				.map(qr -> AbstractProvQuoteInstanceResource.computeFloat(base, qr))
				.orElseGet(() -> new FloatingCost(base));
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

	@Override
	protected ResourceType getType() {
		return ResourceType.STORAGE;
	}

}
