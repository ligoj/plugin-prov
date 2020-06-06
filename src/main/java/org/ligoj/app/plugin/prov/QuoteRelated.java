/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.dao.RestRepository;

/**
 * An object related to a quote. Handle cost cost and association validation against the quote.
 *
 * @param <C> The related entity.
 */
public interface QuoteRelated<C extends Costed> {

	/**
	 * Average hours in one month.
	 */
	double HOURS_BY_MONTH = 24d * 365d / 12d;

	/**
	 * Return the {@link SubscriptionResource} instance. Used to resolve the related subscription and validate the
	 * visibility.
	 *
	 * @return The {@link SubscriptionResource} instance.
	 */
	SubscriptionResource getSubscriptionResource();

	/**
	 * Return the {@link ProvQuoteRepository} instance. Used to resolve the right quote.
	 *
	 * @return The {@link ProvQuoteRepository} instance.
	 */
	ProvQuoteRepository getRepository();

	/**
	 * Return the quote associated to the given subscription. The visibility is checked.
	 *
	 * @param subscription The linked subscription.
	 * @return The quote if the visibility has been checked.
	 */
	default ProvQuote getQuoteFromSubscription(final int subscription) {
		return getRepository().findBy("subscription", getSubscriptionResource().checkVisible(subscription));
	}

	/**
	 * Request a cost update of the given entity and report the delta to the global cost. The changes are persisted.
	 *
	 * @param repository  The repository of the entity holding the cost.
	 * @param entity      The entity holding the cost.
	 * @param costUpdater The function used to compute the new cost.
	 * @param <T>         The entity type holding the cost.
	 * @return The new computed cost.
	 */
	default <T extends Costed> UpdatedCost newUpdateCost(final RestRepository<T, Integer> repository, final T entity,
			final Function<T, FloatingCost> costUpdater) {

		// Update the total cost, applying the delta cost
		final var floatingCost = addCost(entity, costUpdater);
		repository.saveAndFlush(entity);

		final var cost = new UpdatedCost(entity.getId());
		cost.setCost(floatingCost);
		cost.setTotal(entity.getConfiguration().toFloatingCost());
		return cost;
	}

	/**
	 * Add a cost to the quote related to given resource entity. The global cost is not deeply computed, only delta is
	 * applied.
	 *
	 * @param entity      The configured entity, related to a quote.
	 * @param costUpdater The function used to compute the new cost.
	 * @param <T>         The entity type holding the cost.
	 * @return The new computed cost.
	 */
	default <T extends Costed> FloatingCost addCost(final T entity, final Function<T, FloatingCost> costUpdater) {
		// Save the previous costs
		final double oldCost = ObjectUtils.defaultIfNull(entity.getCost(), 0d);
		final double oldMaxCost = ObjectUtils.defaultIfNull(entity.getMaxCost(), 0d);

		// Process the update of this entity
		final var newCost = costUpdater.apply(entity);

		// Report the delta to the quote
		addCost(entity, oldCost, oldMaxCost);
		return newCost;
	}

	/**
	 * Add a cost to the quote related to given resource entity. The global cost is not deeply computed, only delta is
	 * applied.
	 *
	 * @param entity     The configured entity, related to a quote.
	 * @param oldCost    The old cost.
	 * @param oldMaxCost The old maximum cost.
	 * @param <T>        The entity type holding the cost.
	 */
	default <T extends Costed> void addCost(final T entity, final double oldCost, final double oldMaxCost) {
		final var quote = entity.getConfiguration();
		if ((entity.getCost() - oldCost + entity.getMaxCost() - oldMaxCost) != 0) {
			// Report the delta to the quote
			synchronized (quote) {
				quote.setCostNoSupport(round(quote.getCostNoSupport() + entity.getCost() - oldCost));
				quote.setMaxCostNoSupport(round(quote.getMaxCostNoSupport() + entity.getMaxCost() - oldMaxCost));
			}
		}
	}

	/**
	 * Update the quote's cost minimal and maximal values.
	 *
	 * @param quote The quote entity.
	 * @param fc    The cost to add. May be a negative value.
	 * @return The formal {@code fc} parameter.
	 */
	default FloatingCost addCost(final ProvQuote quote, final FloatingCost fc) {
		synchronized (quote) {
			quote.setCostNoSupport(round(quote.getCostNoSupport() + fc.getMin()));
			quote.setMaxCostNoSupport(round(quote.getMaxCostNoSupport() + fc.getMax()));
			quote.setInitialCost(round(quote.getInitialCost() + fc.getInitial()));
			quote.setMaxInitialCost(round(quote.getMaxCostNoSupport() + fc.getMaxInitial()));
		}
		return fc;
	}

	/**
	 * Round a cost to eliminate floating point artifact, and without required {@link BigDecimal} usage (not yet)
	 *
	 * @param value The value to round.
	 * @return The rounded value with 4 decimals.
	 */
	default double round(final double value) {
		return Math.round(value * 1000d) / 1000d;
	}

	/**
	 * Update the actual monthly cost of given resource.
	 *
	 * @param qr           The {@link AbstractQuote} to update cost.
	 * @param costProvider The cost provider.
	 * @param <T>          The entity type holding the cost.
	 * @return The new (min/max) cost.
	 */
	default <T extends AbstractQuote<?>> FloatingCost updateCost(final T qr,
			final Function<T, FloatingCost> costProvider) {
		final var cost = costProvider.apply(qr);
		qr.setCost(round(cost.getMin()));
		qr.setMaxCost(round(cost.getMax()));
		return new FloatingCost(qr.getCost(), qr.getMaxCost(), qr.getInitialCost(), qr.getMaxInitialCost(),
				qr.isUnboundCost());
	}

	/**
	 * Check and return the non <code>null</code> object.
	 *
	 * @param object The object to test.
	 * @param name   The object name. Used for the exception when <code>null</code>.
	 * @param <T>    The entity type.
	 * @return the {@code object} when not <code>null</code>.
	 */
	default <T> T assertFound(final T object, final String name) {
		// Find the scoped location
		return Optional.ofNullable(object).orElseThrow(() -> new EntityNotFoundException(name));
	}

	/**
	 * Refresh the resources and the related cost. This is a full optimization where lookups of the best prices is
	 * performed. Note only the given entity is updated, the related quote's cost is not updated.
	 *
	 * @param costed The entity to refresh.
	 * @return The new computed price.
	 */
	FloatingCost refresh(final C costed);
}
