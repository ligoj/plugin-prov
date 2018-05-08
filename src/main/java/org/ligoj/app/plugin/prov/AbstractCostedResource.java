package org.ligoj.app.plugin.prov;

import java.util.function.Consumer;

import javax.transaction.Transactional;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * The common features of a costed entity.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public abstract class AbstractCostedResource<C extends AbstractQuoteResource> implements QuoteRelated<C> {

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
	 * @param resourceType
	 *            The resource type you are looking for. Will be used to generate the error when not found.
	 * @param lookup
	 *            The expected not null lookup.
	 * @param context
	 *            The key identifier of the lookup. Will be used to generate the error when not found.
	 * @return The price of the not <code>null</code> lookup. Never <code>null</code>.
	 * @param <T>
	 *            The price type.
	 */
	protected <T extends AbstractPrice<? extends AbstractNamedEntity<?>>> T validateLookup(final String resourceType,
			final AbstractComputedPrice<T> lookup, final String context) {
		if (lookup == null) {
			throw new ValidationJsonException(resourceType, "no-match-" + resourceType, "resource", context);
		}
		return lookup.getPrice();
	}

	/**
	 * Update the total cost of the associated quote, and then delete a configured entity.
	 *
	 * @param repository
	 *            The repository managing the entity to delete.
	 * @param id
	 *            The entity's identifier to delete.
	 * @param callback
	 *            The {@link Consumer} call after the updated cost and before the actual deletion.
	 * @return The new cost.
	 * @param <T>
	 *            The quote resource type.
	 */
	protected <T extends AbstractQuoteResource> FloatingCost deleteAndUpdateCost(
			final RestRepository<T, Integer> repository, final Integer id, final Consumer<T> callback) {
		// Check the entity exists and is visible
		final T entity = resource.findConfigured(repository, id);

		// Remove the cost of this entity
		addCost(entity, e -> {
			e.setCost(0d);
			e.setMaxCost(0d);
			return new FloatingCost(0);
		});

		// Callback before the deletion
		callback.accept(entity);

		// Delete the entity
		repository.deleteById(id);
		return toFloatingCost(entity.getConfiguration());

	}

	/**
	 * Update the actual monthly cost of given resource.
	 *
	 * @param qr
	 *            The {@link Costed} to update cost.
	 * @return The new cost.
	 */
	protected FloatingCost updateCost(final C qr) {
		return updateCost(qr, this::getCost);
	}

	/**
	 * Compute the monthly cost of the given resource.
	 *
	 * @param qr
	 *            The {@link Costed} resource to evaluate.
	 * @return The cost of this instance.
	 */
	protected abstract FloatingCost getCost(final C qr);

	/**
	 * Return the effective location applied to the given resource.
	 *
	 * @param qr
	 *            The {@link Costed} resource to evaluate.
	 * @return The related location. Never <code>null</code>.
	 */
	protected ProvLocation getLocation(final AbstractQuoteResource qr) {
		return qr.getLocation() == null ? qr.getConfiguration().getLocation() : qr.getLocation();
	}
}
