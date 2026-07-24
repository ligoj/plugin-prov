/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.dao.ProvOptimizerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteContainerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteFunctionRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.container.ProvQuoteContainerResource;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.function.ProvQuoteFunctionResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Server-side bulk operations over the compute resources of a quote: one transaction, one authoritative cost recompute
 * at the end. The patch semantics are tri-state (see {@link QuoteBulkEditionVo}); after a patch each resource's price
 * is re-resolved against the catalog under its new constraints — a resource that no longer matches any offer fails the
 * whole bulk (atomic, with the resource name in the error), never a silent partial state.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteBulkResource {

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;
	@Autowired
	private ProvQuoteDatabaseResource qbResource;
	@Autowired
	private ProvQuoteContainerResource qcResource;
	@Autowired
	private ProvQuoteFunctionResource qfResource;
	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;
	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;
	@Autowired
	private ProvQuoteContainerRepository qcRepository;
	@Autowired
	private ProvQuoteFunctionRepository qfRepository;

	@Autowired
	private ProvUsageRepository usageRepository;
	@Autowired
	private ProvBudgetRepository budgetRepository;
	@Autowired
	private ProvOptimizerRepository optimizerRepository;

	/**
	 * Apply a patch to several compute resources at once, re-pricing each under its new constraints, then recompute the
	 * quote cost once.
	 *
	 * @param subscription The subscription identifier.
	 * @param type         The compute resource type (instance / database / container / function).
	 * @param vo           The identifiers and the tri-state patch.
	 * @return The new quote total.
	 */
	@PUT
	@Path("{subscription:\\d+}/bulk/{type}")
	public Floating update(@PathParam("subscription") final int subscription,
			@PathParam("type") final ResourceType type, final QuoteBulkEditionVo vo) {
		subscriptionResource.checkVisible(subscription);
		final var quote = resource.getQuoteFromSubscription(subscription);
		final var provider = quote.getSubscription().getNode().getRefined().getId();
		for (final var id : vo.getIds()) {
			final var entity = findOwned(type, id, subscription);
			final var oldLocation = entity.getResolvedLocation();
			patch(entity, vo, subscription, provider);
			// Re-resolve the price under the new constraints — throws (with the resource
			// name) when nothing matches any more, rolling back the whole bulk.
			refresh(type, entity);
			if (!oldLocation.equals(entity.getResolvedLocation())) {
				// The attached storages' prices depend on the location: refresh them too.
				CollectionUtils.emptyIfNull(entity.getStorages()).forEach(s -> {
					qsResource.refresh(s);
					qsResource.refreshCost(s);
				});
			}
		}
		return resource.updateCost(subscription);
	}

	/**
	 * Delete several compute resources at once (with their attached storages, tags and network links), then recompute
	 * the quote cost once.
	 *
	 * @param subscription The subscription identifier.
	 * @param type         The compute resource type.
	 * @param ids          The identifiers of the resources to delete. All must belong to the subscription's quote.
	 * @return The new quote total.
	 */
	@POST
	@Path("{subscription:\\d+}/bulk/{type}/delete")
	public Floating delete(@PathParam("subscription") final int subscription,
			@PathParam("type") final ResourceType type, final List<Integer> ids) {
		subscriptionResource.checkVisible(subscription);
		for (final var id : ids) {
			findOwned(type, id, subscription);
			switch (type) {
			case INSTANCE -> qiResource.delete(id);
			case DATABASE -> qbResource.delete(id);
			case CONTAINER -> qcResource.delete(id);
			default -> qfResource.delete(id);
			}
		}
		return resource.updateCost(subscription);
	}

	/** Apply the tri-state patch fields onto the entity: null = untouched, empty = cleared, value = resolved+set. */
	private void patch(final AbstractQuoteVm<?> entity, final QuoteBulkEditionVo vo, final int subscription,
			final String provider) {
		if (vo.getUsage() != null) {
			entity.setUsage(vo.getUsage().isEmpty() ? null
					: required(usageRepository.findByName(subscription, vo.getUsage()), vo.getUsage()));
		}
		if (vo.getBudget() != null) {
			entity.setBudget(vo.getBudget().isEmpty() ? null
					: required(budgetRepository.findByName(subscription, vo.getBudget()), vo.getBudget()));
		}
		if (vo.getOptimizer() != null) {
			entity.setOptimizer(vo.getOptimizer().isEmpty() ? null
					: required(optimizerRepository.findByName(subscription, vo.getOptimizer()), vo.getOptimizer()));
		}
		if (vo.getLocation() != null) {
			entity.setLocation(vo.getLocation().isEmpty() ? null : resource.findLocation(provider, vo.getLocation()));
		}
		if (vo.getLicense() != null) {
			entity.setLicense(StringUtils.trimToNull(vo.getLicense().toUpperCase()));
		}
	}

	private <T> T required(final T value, final String name) {
		if (value == null) {
			throw new EntityNotFoundException(name);
		}
		return value;
	}

	private Floating refresh(final ResourceType type, final AbstractQuoteVm<?> entity) {
		return switch (type) {
		case INSTANCE -> qiResource.refresh((ProvQuoteInstance) entity);
		case DATABASE -> qbResource.refresh((ProvQuoteDatabase) entity);
		case CONTAINER -> qcResource.refresh((ProvQuoteContainer) entity);
		default -> qfResource.refresh((ProvQuoteFunction) entity);
		};
	}

	/** Load a compute resource by id and check it belongs to the subscription's quote. */
	private AbstractQuoteVm<?> findOwned(final ResourceType type, final int id, final int subscription) {
		final AbstractQuoteVm<?> entity = switch (type) {
		case INSTANCE -> qiRepository.findById(id).orElse(null);
		case DATABASE -> qbRepository.findById(id).orElse(null);
		case CONTAINER -> qcRepository.findById(id).orElse(null);
		case FUNCTION -> qfRepository.findById(id).orElse(null);
		default -> null;
		};
		if (entity == null || entity.getConfiguration().getSubscription().getId().intValue() != subscription) {
			throw new EntityNotFoundException(String.valueOf(id));
		}
		return entity;
	}
}
