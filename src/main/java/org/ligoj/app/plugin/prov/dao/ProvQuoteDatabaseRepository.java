/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuoteDatabase} repository.
 */
public interface ProvQuoteDatabaseRepository extends BaseProvQuoteResourceRepository<ProvQuoteDatabase>, BasePovInstanceBehavior {

	/**
	 * Delete all storages linked to a database linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Override
	@Modifying
	@Query("DELETE FROM ProvQuoteStorage WHERE quoteDatabase IS NOT NULL"
			+ " AND configuration.id IN (SELECT id FROM ProvQuote WHERE subscription.id = :subscription)")
	void deleteAllStorages(int subscription);

	/**
	 * Return identifiers of all storages linked to a database linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Override
	@Query("SELECT id FROM ProvQuoteStorage WHERE quoteDatabase IS NOT NULL"
			+ " AND configuration.subscription.id = :subscription")
	List<Integer> findAllStorageIdentifiers(int subscription);
}
