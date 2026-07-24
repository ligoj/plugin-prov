/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvLookupError;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvLookupError} repository.
 */
public interface ProvLookupErrorRepository extends RestRepository<ProvLookupError, Integer> {

	/**
	 * Return the lookup errors recorded for a compared subscription (CS).
	 *
	 * @param subscription The compared subscription identifier.
	 * @return The recorded errors.
	 */
	List<ProvLookupError> findAllBySubscriptionId(int subscription);

	/**
	 * Return the lookup errors recorded across all compared subscriptions of a main subscription (MS).
	 *
	 * @param mainSubscription The main subscription identifier.
	 * @return The recorded errors.
	 */
	List<ProvLookupError> findAllByMainSubscriptionId(int mainSubscription);

	/**
	 * Delete all the errors recorded for a compared subscription (CS).
	 *
	 * @param subscription The compared subscription identifier.
	 */
	@Modifying
	@Query("DELETE FROM ProvLookupError WHERE subscription.id = :subscription")
	void deleteAllBySubscription(int subscription);
}
