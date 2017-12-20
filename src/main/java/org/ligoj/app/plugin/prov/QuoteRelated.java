package org.ligoj.app.plugin.prov;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.function.Function;

import javax.persistence.EntityNotFoundException;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.dao.RestRepository;

public interface QuoteRelated<C extends Costed> {

	/**
	 * Average hours in one month.
	 */
	double HOURS_BY_MONTH = 24d * 365d / 12d;

	SubscriptionResource getSubscriptionResource();

	ProvQuoteRepository getRepository();

	/**
	 * Return the quote associated to the given subscription. The visibility is
	 * checked.
	 * 
	 * @param subscription
	 *            The linked subscription.
	 * @return The quote if the visibility has been checked.
	 */
	default ProvQuote getQuoteFromSubscription(final int subscription) {
		return getRepository().findBy("subscription", getSubscriptionResource().checkVisibleSubscription(subscription));
	}

	/**
	 * Request a cost update of the given entity and report the delta to the the
	 * global cost. The changes are persisted.
	 */
	default <T extends Costed> UpdatedCost newUpdateCost(final RestRepository<T, Integer> repository, final T entity,
			final Function<T, FloatingCost> costUpdater) {

		// Update the total cost, applying the delta cost
		final FloatingCost floatingCost = addCost(entity, costUpdater);
		repository.saveAndFlush(entity);

		final UpdatedCost cost = new UpdatedCost();
		cost.setId(entity.getId());
		cost.setResourceCost(floatingCost);
		cost.setTotalCost(toFloatingCost(entity.getConfiguration()));
		return cost;
	}

	/**
	 * Add a cost to the quote related to given resource entity. The global cost is
	 * not deeply computed, only delta is applied.
	 * 
	 * @param entity
	 *            The configured entity, related to a quote.
	 * @param costUpdater
	 *            The function used to compute the new cost.
	 * @return The new computed cost.
	 */
	default <T extends Costed> FloatingCost addCost(final T entity, final Function<T, FloatingCost> costUpdater) {
		// Save the previous costs
		final double oldCost = ObjectUtils.defaultIfNull(entity.getCost(), 0d);
		final double oldMaxCost = ObjectUtils.defaultIfNull(entity.getMaxCost(), 0d);

		// Process the update of this entity
		final FloatingCost newCost = costUpdater.apply(entity);

		// Report the delta to the quote
		final ProvQuote configuration = entity.getConfiguration();
		configuration.setCost(round(configuration.getCost() + entity.getCost() - oldCost));
		configuration.setMaxCost(round(configuration.getMaxCost() + entity.getMaxCost() - oldMaxCost));
		return newCost;
	}

	/**
	 * Update the quote's cost minimal and maximal values.
	 * 
	 * @param entity
	 *            The quote entity.
	 * @param fc
	 *            The cost to add. May be a negative value.
	 * @return The formal {@code fc} parameter.
	 */
	default FloatingCost addCost(final ProvQuote entity, final FloatingCost fc) {
		entity.setCost(round(entity.getCost() + fc.getMin()));
		entity.setMaxCost(round(entity.getMaxCost() + fc.getMax()));
		return fc;
	}

	/**
	 * Round a cost to eliminate floating point artifact, and without required
	 * {@link BigDecimal} usage (not yet)
	 * 
	 * @param value
	 *            The value to round.
	 * @return The rounded value with 4 decimals.
	 */
	default double round(final double value) {
		return Math.round(value * 1000d) / 1000d;
	}

	/**
	 * Compute the monthly cost from hourly costs.
	 * 
	 * @param value
	 *            The value to round.
	 * @return The rounded value for one month with 4 decimals.
	 */
	default double toMonthly(final double cost) {
		return toMonthly(cost, 1000);
	}

	/**
	 * Compute the monthly cost from hourly costs.
	 * 
	 * @param value
	 *            The value to round.
	 * @return The rounded value for one month with 4 decimals.
	 */
	default double toMonthly(final double cost, final int mult) {
		return Math.round(cost * mult * HOURS_BY_MONTH) / 1000d;
	}

	/**
	 * Update the actual monthly cost of given resource.
	 * 
	 * @param qr
	 *            The {@link AbstractQuoteResource} to update cost.
	 * @return The new cost.
	 */
	default <T extends AbstractQuoteResource> FloatingCost updateCost(final T qr, Function<T, FloatingCost> costProvider,
			Function<Double, Double> toMonthly) {
		final FloatingCost cost = costProvider.apply(qr);
		qr.setCost(toMonthly.apply(cost.getMin()));
		qr.setMaxCost(toMonthly.apply(cost.getMax()));
		return new FloatingCost(qr.getCost(), qr.getMaxCost(), qr.isUnboundCost());
	}

	/**
	 * Extract the costs from a quote and build a new {@link FloatingCost}
	 * 
	 * @param configuration
	 *            The quote configuration.
	 * @return The built {@link FloatingCost} instance.
	 */
	default FloatingCost toFloatingCost(final ProvQuote configuration) {
		return new FloatingCost(configuration.getCost(), configuration.getMaxCost(), configuration.getUnboundCostCounter() > 0);
	}

	/**
	 * Check and return the non <code>null</code> object.
	 * 
	 * @param object
	 *            The object to test.
	 * @param name
	 *            The object name. Used for the exception when <code>null</code>.
	 * @return the {@code object} when not <code>null</code>.
	 */
	default <T> T assertFound(final T object, final String name) {
		// Find the scoped location
		return Optional.ofNullable(object).orElseThrow(() -> new EntityNotFoundException(name));
	}

	FloatingCost refresh(final C costed);
}
