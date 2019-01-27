/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuoteStorage} repository.
 */
public interface ProvQuoteStorageRepository extends BaseProvQuoteResourceRepository<ProvQuoteStorage> {

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
