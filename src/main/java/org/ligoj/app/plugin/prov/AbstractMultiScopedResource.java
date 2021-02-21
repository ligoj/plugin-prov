/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.ligoj.app.plugin.prov.dao.BaseMultiScopedRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteContainerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractMultiScoped;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ResourceScope;
import org.ligoj.app.plugin.prov.quote.container.ProvQuoteContainerResource;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.NamedBean;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Multiple scoped resource.
 *
 * @param <S> The multiple scoped resource type.
 * @param <R> The multiple scoped repository type.
 * @param <V> The multiple scoped VO type.
 */
@Transactional
public abstract class AbstractMultiScopedResource<S extends AbstractMultiScoped, R extends BaseMultiScopedRepository<S>, V extends NamedBean<Integer>> {

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected PaginationJson paginationJson;

	@Autowired
	protected ProvResource resource;

	@Autowired
	protected ProvQuoteInstanceResource qiResource;

	@Autowired
	protected ProvQuoteDatabaseResource qbResource;

	@Autowired
	protected ProvQuoteContainerResource qcResource;

	@Autowired
	protected ProvQuoteInstanceRepository qiRepository;

	@Autowired
	protected ProvQuoteDatabaseRepository qbRepository;

	@Autowired
	protected ProvQuoteContainerRepository qcRepository;

	@Autowired
	protected ProvBudgetResource bRessource;

	/**
	 * Quote data getter.
	 */
	private final Function<ResourceScope, S> quoteGetter;

	/**
	 * Quote data setter.
	 */
	private final BiConsumer<ResourceScope, S> quoteSetter;

	/**
	 * Resource creator.
	 */
	private final Supplier<S> newEntity;

	/**
	 * Constructor for data access properties.
	 * 
	 * @param quoteGetter Quote data getter.
	 * @param quoteSetter Quote data setter.
	 * @param newEntity   Resource creator.
	 */
	protected AbstractMultiScopedResource(final Function<ResourceScope, S> quoteGetter,
			final BiConsumer<ResourceScope, S> quoteSetter, final Supplier<S> newEntity) {
		this.quoteGetter = quoteGetter;
		this.quoteSetter = quoteSetter;
		this.newEntity = newEntity;
	}

	protected abstract R getRepository();

	/**
	 * Return the resources available for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param uriInfo      filter data.
	 * @return The available resources for the given subscription.
	 */
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<S> findAll(@PathParam("subscription") final int subscription, @Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				getRepository().findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Delete a resource. When the resource is associated to a quote or a resource, it is replaced by a
	 * <code>null</code> reference.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param id           The resource identifier.
	 * @return The updated cost. Only relevant when at least one resource was associated to this resource.
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("{id:\\d+}")
	public UpdatedCost delete(final int subscription, final int id) {
		final var entity = resource.findConfigured(getRepository(), id, subscription);
		final var quote = entity.getConfiguration();
		final var cost = new UpdatedCost(entity.getId());

		// Get the related resources
		final var instances = getRelated(getRepository()::findRelatedInstances, entity);
		final var databases = getRelated(getRepository()::findRelatedDatabases, entity);
		final var containers = getRelated(getRepository()::findRelatedContainers, entity);

		if (entity.equals(quoteGetter.apply(quote))) {
			// Update cost of all instances without explicit resource
			quoteSetter.accept(quote, null);
		}
		instances.forEach(i -> quoteSetter.accept(i, null));
		databases.forEach(i -> quoteSetter.accept(i, null));
		containers.forEach(i -> quoteSetter.accept(i, null));
		bRessource.lean(quote, instances, databases, containers, cost.getRelated());

		// All references are deleted, delete the parent entity
		getRepository().delete(entity);

		// Update accordingly the support costs
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Return a stream of the filtered resources related to the given budget/usage.
	 * @param fetcher The function returning the related resources to a given budget/usage.
	 * @param entity The budget/usage to evaluate.
	 * @param <P> The price type of the resource.
	 * @param <T> The price term of the resource.
	 * @param <C> The related resource type.
	 * @return The filtered resources related to the given budget.
	 */
	protected <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>> Stream<C> getRelatedStream(
			final Function<S, Stream<C>> fetcher, final S entity) {
		return fetcher.apply(entity);
	}

	/**
	 * Return the filtered resources related to the given budget.
	 * 
	 * @param <P> The price type of the resource.
	 * @param <T> The price term of the resource.
	 * @param <C> The related resource type.
	 * @return The filtered resources related to the given budget.
	 */
	protected <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>> List<C> getRelated(
			final Function<S, Stream<C>> fetcher, final S entity) {
		return getRelatedStream(fetcher, entity).collect(Collectors.toList());
	}

	/**
	 * Create the resource inside a quote. No cost are updated during this operation since this new resource is not yet
	 * used.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param vo           The quote resource.
	 * @return The created resource identifier.
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(@PathParam("subscription") final int subscription, final V vo) {
		final var entity = newEntity.get();
		entity.setConfiguration(resource.getQuoteFromSubscription(subscription));
		return saveOrUpdate(entity, vo).getId();
	}

	/**
	 * Update the resource inside a quote. The computed cost are recursively updated from the related instances to the
	 * quote total cost.<br>
	 * The cost of all instances related to this resource will be updated to get the new price.<br>
	 * An instance related to this resource is either an instance explicitly linked to this resource, either an instance
	 * linked to a quote having this resource as default.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param vo           The new quote resource data.
	 * @return The updated cost. Only relevant when at least one resource was associated to this resource.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(@PathParam("subscription") final int subscription, final V vo) {
		return saveOrUpdate(resource.findConfigured(getRepository(), vo.getId(), subscription), vo);
	}

	/**
	 * Save or update the given resource entity from the VO. The computed cost are recursively updated from the related
	 * instances to the quote total cost.<br>
	 * The cost of all instances related to this resource will be updated to get the new term and related price.<br>
	 * An instance related to this resource is either an instance explicitly linked to this resource, either an instance
	 * linked to a quote having this resource as default.
	 * 
	 * @param entity The target entity to update.
	 * @param vo     The new quote resource data.
	 * @return The updated cost data.
	 */
	protected abstract UpdatedCost saveOrUpdate(final S entity, final V vo);

}
