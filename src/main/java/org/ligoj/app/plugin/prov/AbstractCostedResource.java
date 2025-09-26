/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.function.Consumer;

import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.Getter;

/**
 * The common features of a costed entity.
 *
 * @param <C> Quoted resource type.
 * @param <P> Quoted resource price type.
 * @param <T> Quoted resource price type's type.
 */
public abstract class AbstractCostedResource<T extends AbstractCodedEntity, P extends AbstractPrice<T>, C extends AbstractQuote<P>>
		implements QuoteRelated<C> {

	@Autowired
	protected PaginationJson paginationJson;

	@Autowired
	@Getter
	protected SubscriptionResource subscriptionResource;

	@Autowired
	@Getter
	private ProvQuoteRepository repository;

	@Autowired
	protected ProvResource resource;

	/**
	 * Check the lookup succeed.
	 *
	 * @param resourceType The resource type you are looking for. Will be used to generate the error when not found.
	 * @param lookup       The expected not null lookup.
	 * @param context      The key identifier of the lookup. Will be used to generate the error when not found.
	 * @return The price of the not <code>null</code> lookup. Never <code>null</code>.
	 */
	public P validateLookup(final ResourceType resourceType, final AbstractLookup<P> lookup, final String context) {
		if (lookup == null) {
			throw new ValidationJsonException(resourceType.name().toLowerCase(), "no-match-" + resourceType.name().toLowerCase(), "resource", context);
		}
		return lookup.getPrice();
	}

	/**
	 * Update the total cost of the associated quote, and then delete a configured entity.
	 *
	 * @param repository The repository managing the entity to delete.
	 * @param id         The entity's identifier to delete.
	 * @param callback   The {@link Consumer} call after the updated cost and before the actual deletion.
	 * @return deleted quote resource.
	 * @param <Q> The quote resource type.
	 */
	protected <Q extends AbstractQuote<?>> Q deleteAndUpdateCost(final RestRepository<Q, Integer> repository,
			final Integer id, final Consumer<Q> callback) {
		// Check the entity exists and is visible
		final var entity = resource.findConfigured(repository, id);

		// Remove the cost of this entity
		addCost(entity, e -> {
			e.setCost(0d);
			e.setMaxCost(0d);
			e.setCo2(0d);
			e.setMaxCo2(0d);
			e.setInitialCost(0d);
			e.setMaxInitialCost(0d);
			return new Floating();
		});

		// Callback before the deletion
		if (callback != null) {
			callback.accept(entity);
		}

		// Delete the entity
		repository.deleteById(id);

		return entity;
	}

	/**
	 * Update the actual monthly cost of given resource.
	 *
	 * @param qr The {@link Costed} to update cost.
	 * @return The new cost.
	 */
	public Floating updateCost(final C qr) {
		return updateCost(qr, this::getCost);
	}

	/**
	 * Compute the monthly cost of the given resource.
	 *
	 * @param qr The {@link Costed} resource to evaluate.
	 * @return The cost of this instance.
	 */
	protected abstract Floating getCost(final C qr);
}
