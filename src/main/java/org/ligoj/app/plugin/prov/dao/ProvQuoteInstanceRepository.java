/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ProvQuoteInstance} repository.
 */
public interface ProvQuoteInstanceRepository extends ProvQuoteResourceRepository<ProvQuoteInstance> {

	/**
	 * Delete all instance linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Modifying
	@Query("DELETE FROM #{#entityName} WHERE configuration.id IN"
			+ " (SELECT id FROM ProvQuote WHERE subscription.id = :subscription)")
	void deleteAllBySubscription(@Param("subscription") int subscription);

	/**
	 * Return the instance quote details from the related subscription.
	 *
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The instance quote details with the optional linked instance.
	 */
	@Query("FROM #{#entityName} AS qi INNER JOIN FETCH qi.price qsp INNER JOIN FETCH qsp.type"
			+ " WHERE qi.configuration.subscription.id = :subscription")
	List<ProvQuoteInstance> findAll(int subscription);
}
