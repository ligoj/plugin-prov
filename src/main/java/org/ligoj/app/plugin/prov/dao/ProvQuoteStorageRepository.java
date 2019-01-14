/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ProvQuoteStorage} repository.
 */
public interface ProvQuoteStorageRepository extends BaseProvQuoteResourceRepository<ProvQuoteStorage> {

	/**
	 * Delete all storages linked to an instance linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Modifying
	@Query("DELETE FROM #{#entityName} WHERE quoteInstance IS NOT NULL"
			+ " AND configuration.id IN (SELECT id FROM ProvQuote WHERE subscription.id = :subscription)")
	void deleteAllAttached(@Param("subscription") int subscription);

	/**
	 * Return identifiers of all storages linked to an instance linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE quoteInstance IS NOT NULL"
			+ " AND configuration.subscription.id = :subscription")
	List<Integer> findAllAttachedIdentifiers(@Param("subscription") int subscription);

	/**
	 * Return the storage quote details from the related subscription.
	 *
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The storage quote details with the optional linked instance.
	 */
	@Override
	@Query("FROM #{#entityName} AS qs INNER JOIN FETCH qs.price qsp INNER JOIN FETCH qsp.type LEFT JOIN FETCH qs.quoteInstance"
			+ " WHERE qs.configuration.subscription.id = :subscription")
	List<ProvQuoteStorage> findAll(int subscription);

}
