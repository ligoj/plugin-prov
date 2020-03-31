/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.util.function.Function;

import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvType;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The resource part of the provisioning.
 *
 * @param <C> Quoted resource type.
 * @param <P> Quoted resource price type.
 * @param <T> Quoted resource price type type.
 * @since 1.8.5
 */
public abstract class AbstractProvQuoteResource<T extends ProvType, P extends AbstractPrice<T>, C extends AbstractQuote<P>>
		extends AbstractCostedResource<T, P, C> {

	@Autowired
	protected ProvTagResource tagResource;

	@Autowired
	protected ProvNetworkResource networkResource;

	/**
	 * Return the resource type managed by this service.
	 *
	 * @return The resource type managed by this service.
	 */
	protected abstract ResourceType getType();

	protected abstract BaseProvQuoteRepository<C> getResourceRepository();

	/**
	 * Delete all resources type from a quote. The total cost is updated.
	 *
	 * @param subscription The related subscription.
	 * @return The updated computed cost.
	 */
	protected UpdatedCost deleteAll(final int subscription) {
		final var quote = resource.getQuoteFromSubscription(subscription);
		final var cost = new UpdatedCost(0);
		tagResource.onDeleteAll(getType(), quote.getId());
		networkResource.onDeleteAll(getType(), quote.getId());

		// Delete all resources
		final var repository = getResourceRepository();
		cost.getDeleted().put(getType(), repository.findAllIdentifiers(subscription));
		repository.deleteAll(repository.findAllBy("configuration.subscription.id", subscription));
		repository.flush();

		// Update the cost. Note the effort could be reduced to a simple subtract of deleted resource costs.
		resource.updateCost(subscription);
		return resource.refreshSupportCost(cost, quote);
	}

	protected void saveOrUpdate(final C entity, final QuoteTagSupport vo) {
		// Add tags
		tagResource.replaceTags(vo.getTags(), entity);
	}

	/**
	 * Delete a storage from a quote. The total cost is updated.
	 *
	 * @param id The {@link ProvQuoteStorage}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	protected UpdatedCost delete(final int id) {
		tagResource.onDelete(getType(), id);
		networkResource.onDelete(getType(), id);
		return resource.refreshSupportCost(new UpdatedCost(id),
				deleteAndUpdateCost(getResourceRepository(), id, Function.identity()::apply));
	}
}
