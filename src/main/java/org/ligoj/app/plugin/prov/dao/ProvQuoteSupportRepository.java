/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuoteSupport} repository.
 */
public interface ProvQuoteSupportRepository extends ProvQuoteResourceRepository<ProvQuoteSupport> {

	/**
	 * Return the support quote details from the related subscription.
	 *
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The support quote details with the optional linked instance.
	 */
	@Query("FROM #{#entityName} AS qs INNER JOIN FETCH qs.price qsp INNER JOIN FETCH qsp.type"
			+ " WHERE qs.configuration.subscription.id = :subscription")
	List<ProvQuoteSupport> findAll(int subscription);

}
