/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ProvQuoteInstance} repository.
 */
public interface ProvQuoteInstanceRepository extends RestRepository<ProvQuoteInstance, Integer> {

	/**
	 * Delete all instance linked to the given subscription.
	 * 
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Modifying
	@Query("DELETE FROM ProvQuoteInstance WHERE id IN"
			+ " (SELECT id FROM ProvQuoteInstance WHERE configuration.subscription.id = :subscription)")
	void deleteAllBySubscription(@Param("subscription") int subscription);
}
