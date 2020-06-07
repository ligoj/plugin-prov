/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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

import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Budget part of provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvBudgetResource {

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private PaginationJson paginationJson;

	@Autowired
	private ProvBudgetRepository repository;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteDatabaseResource qbResource;

	/**
	 * Return the budgets available for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the budgets from the associated provider.
	 * @param uriInfo      filter data.
	 * @return The available budgets for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/budget")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvBudget> findAll(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				repository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Create the budget inside a quote. No cost are updated during this operation since this new {@link ProvBudget} is
	 * not yet used.
	 *
	 * @param subscription The subscription identifier, will be used to filter the budgets from the associated provider.
	 * @param vo           The quote budget.
	 * @return The created budget identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/budget")
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(@PathParam("subscription") final int subscription, final BudgetEditionVo vo) {
		final var entity = new ProvBudget();
		entity.setConfiguration(resource.getQuoteFromSubscription(subscription));
		return saveOrUpdate(entity, vo).getId();
	}

	/**
	 * Update the budget inside a quote. The computed cost are recursively updated from the related instances to the
	 * quote total cost.<br>
	 * The cost of all instances related to this budget will be updated to get the new price.<br>
	 * An instance related to this budget is either an instance explicitly linked to this budget, either an instance
	 * linked to a quote having this budget as default.
	 *
	 * @param subscription The subscription identifier, will be used to filter the budgets from the associated provider.
	 * @param vo           The new quote budget data.
	 * @return The updated cost. Only relevant when at least one resource was associated to this budget.
	 */
	@PUT
	@Path("{subscription:\\d+}/budget")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(@PathParam("subscription") final int subscription, final BudgetEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(repository, vo.getId(), subscription), vo);
	}

	/**
	 * Save or update the given budget entity from the {@link BudgetEditionVo}. The computed cost are recursively
	 * updated from the related instances to the quote total cost.<br>
	 * The cost of all instances related to this budget will be updated to get the new term and related price.<br>
	 * An instance related to this budget is either an instance explicitly linked to this budget, either an instance
	 * linked to a quote having this budget as default.
	 */
	private UpdatedCost saveOrUpdate(final ProvBudget entity, final BudgetEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setName(vo.getName());
		entity.setInitialCost(vo.getInitialCost());

		// Prepare the updated cost of updated instances
		final var costs = Collections
				.synchronizedMap(new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class));
		final var quote = entity.getConfiguration();

		// Fetch the budgets of this quotes
		quote.getBudgets().size();

		if (entity.getId() != null) {
			// This is an update, update the cost of all related instances
			// Reset the remaining initial cost
			entity.setRemainingBudget(entity.getInitialCost());
			final var isDefault = entity.equals(quote.getBudget());

			// Get all related resources
			var instances = getRelated(quote.getInstances(), entity, isDefault);
			var databases = getRelated(quote.getDatabases(), entity, isDefault);

			// while remaining
			var oldRemaining = entity.getRemainingBudget();
			final var qbPrices = new HashMap<ProvQuoteDatabase, FloatingPrice<ProvDatabasePrice>>();
			final var qiPrices = new HashMap<ProvQuoteInstance, FloatingPrice<ProvInstancePrice>>();
			compute(databases, qbPrices, qbResource, oldRemaining);
			compute(instances, qiPrices, qiResource, oldRemaining);

			// Pack the prices
			// TODO
			updatePrice(qiPrices, ResourceType.INSTANCE, costs, qiResource);
			updatePrice(qbPrices, ResourceType.DATABASE, costs, qbResource);
		}

		repository.saveAndFlush(entity);
		final var cost = new UpdatedCost(entity.getId());
		cost.setRelated(costs);

		// Update accordingly the support costs
		return resource.refreshSupportCost(cost, quote);
	}

	private <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>> void updatePrice(
			final Map<C, FloatingPrice<P>> prices, final ResourceType type,
			final Map<ResourceType, Map<Integer, FloatingCost>> costs,
			final AbstractProvQuoteInstanceResource<T, P, C, ?, ?, ?> resource) {
		prices.forEach((i, price) -> costs.computeIfAbsent(type, k -> new HashMap<>()).put(i.getId(),
				resource.addCost(i, qi -> {
					qi.setPrice(price.getPrice());
					return resource.updateCost(qi);
				})));
	}

	private <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>> List<C> getRelated(
			final List<C> instances, final ProvBudget entity, final boolean includesNull) {
		return instances.stream().filter(i -> i.getBudget() == null ? includesNull : i.getBudget().equals(entity))
				.collect(Collectors.toList());
	}

	private <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>> void compute(
			final List<C> nodes, final Map<C, FloatingPrice<P>> prices,
			final AbstractProvQuoteInstanceResource<T, P, C, ?, ?, ?> resource, final double initialCost) {
		this.resource.newStream(nodes).forEach(i -> prices.put(i, resource.getNewPrice(i)));
	}

	/**
	 * Delete an budget. When the budget is associated to a quote or a resource, it is replaced by a <code>null</code>
	 * reference.
	 *
	 * @param subscription The subscription identifier, will be used to filter the budgets from the associated provider.
	 * @param id           The {@link ProvBudget} identifier.
	 * @return The updated cost. Only relevant when at least one resource was associated to this budget.
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("{subscription:\\d+}/budget/{id:\\d+}")
	public UpdatedCost delete(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		final var entity = resource.findConfigured(repository, id, subscription);
		final var quote = entity.getConfiguration();
		final var cost = new UpdatedCost(entity.getId());
		// Prepare the updated cost of updated instances
		final var costs = cost.getRelated();
		// Update the cost of all related instances
		if (entity.equals(quote.getBudget())) {
			// Update cost of all instances without explicit budget
			quote.setBudget(null);
			quote.getInstances().stream().filter(i -> i.getBudget() == null)
					.forEach(i -> costs.computeIfAbsent(ResourceType.INSTANCE, k -> new HashMap<>()).put(i.getId(),
							qiResource.addCost(i, qiResource::refresh)));
		}
		quote.getInstances().stream().filter(i -> entity.equals(i.getBudget())).peek(i -> i.setBudget(null))
				.forEach(i -> costs.computeIfAbsent(ResourceType.STORAGE, k -> new HashMap<>()).put(i.getId(),
						qiResource.addCost(i, qiResource::refresh)));

		// All references are deleted, delete the budget entity
		repository.delete(entity);

		// Update accordingly the support costs
		return resource.refreshSupportCost(cost, quote);
	}

}
