/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.transaction.Transactional;
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

import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Usage part of provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvUsageResource {

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	private ProvUsageRepository repository;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	/**
	 * Return the usages available for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available usages for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/usage")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvUsage> findAll(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				repository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Create the usage inside a quote. No cost are updated during this operation since this new {@link ProvUsage} is
	 * not yet used.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param vo
	 *            The quote usage.
	 * @return The created usage identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/usage")
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(@PathParam("subscription") final int subscription, final UsageEditionVo vo) {
		final ProvUsage entity = new ProvUsage();
		entity.setConfiguration(resource.getQuoteFromSubscription(subscription));
		return saveOrUpdate(entity, vo).getId();
	}

	/**
	 * Update the usage inside a quote. The computed cost are recursively updated from the related instances to the
	 * quote total cost.<br>
	 * The cost of all instances related to this usage will be updated to get the new price.<br>
	 * An instance related to this usage is either an instance explicitly linked to this usage, either an instance
	 * linked to a quote having this usage as default.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param name
	 *            The quote usage's name to update.
	 * @param vo
	 *            The new quote usage data.
	 * @return The updated cost. Only relevant when at least one resource was associated to this usage.
	 */
	@PUT
	@Path("{subscription:\\d+}/usage/{name}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(@PathParam("subscription") final int subscription, @PathParam("name") final String name,
			final UsageEditionVo vo) {
		return saveOrUpdate(resource.findConfiguredByName(repository, name, subscription), vo);
	}

	/**
	 * Save or update the given usage entity from the {@link UsageEditionVo}. The computed cost are recursively updated
	 * from the related instances to the quote total cost.<br>
	 * The cost of all instances related to this usage will be updated to get the new term and related price.<br>
	 * An instance related to this usage is either an instance explicitly linked to this usage, either an instance
	 * linked to a quote having this usage as default.
	 */
	private UpdatedCost saveOrUpdate(final ProvUsage entity, final UsageEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setRate(vo.getRate());
		entity.setDuration(vo.getDuration());
		entity.setName(vo.getName());
		entity.setStart(vo.getStart());

		// Prepare the updated cost of updated instances
		final Map<ResourceType, Map<Integer, FloatingCost>> costs = new EnumMap<>(ResourceType.class);
		final ProvQuote quote = entity.getConfiguration();
		if (entity.getId() != null) {

			// Update the cost of all related instances
			if (entity.equals(quote.getUsage())) {
				// Update cost of all instances without explicit usage
				quote.getInstances().stream().filter(i -> i.getUsage() == null)
						.forEach(i -> costs.computeIfAbsent(ResourceType.INSTANCE, k -> new HashMap<>()).put(i.getId(),
								qiResource.addCost(i, qiResource::refresh)));
			}
			quote.getInstances().stream().filter(i -> entity.equals(i.getUsage()))
					.forEach(i -> costs.computeIfAbsent(ResourceType.STORAGE, k -> new HashMap<>()).put(i.getId(),
							qiResource.addCost(i, qiResource::refresh)));

			// Save and update the costs
		}

		repository.saveAndFlush(entity);
		final UpdatedCost cost = new UpdatedCost(entity.getId());
		cost.setRelated(costs);

		// Update accordingly the support costs
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Delete an usage. When the usage is associated to a quote or a resource, it is replaced by a <code>null</code>
	 * reference.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param name
	 *            The {@link ProvUsage} name.
	 * @return The updated cost. Only relevant when at least one resource was associated to this usage.
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("{subscription:\\d+}/usage/{name}")
	public UpdatedCost delete(@PathParam("subscription") final int subscription, @PathParam("name") final String name) {
		final ProvUsage entity = resource.findConfiguredByName(repository, name, subscription);
		final ProvQuote quote = entity.getConfiguration();
		final UpdatedCost cost = new UpdatedCost(entity.getId());
		// Prepare the updated cost of updated instances
		final Map<ResourceType, Map<Integer, FloatingCost>> costs = cost.getRelated();
		// Update the cost of all related instances
		if (entity.equals(quote.getUsage())) {
			// Update cost of all instances without explicit usage
			quote.setUsage(null);
			quote.getInstances().stream().filter(i -> i.getUsage() == null)
					.forEach(i -> costs.computeIfAbsent(ResourceType.INSTANCE, k -> new HashMap<>()).put(i.getId(),
							qiResource.addCost(i, qiResource::refresh)));
		}
		quote.getInstances().stream().filter(i -> entity.equals(i.getUsage())).peek(i -> i.setUsage(null))
				.forEach(i -> costs.computeIfAbsent(ResourceType.STORAGE, k -> new HashMap<>()).put(i.getId(),
						qiResource.addCost(i, qiResource::refresh)));

		// All references are deleted, delete the usage entity
		repository.delete(entity);

		// Update accordingly the support costs
		return resource.refreshSupportCost(cost, quote);
	}

}
