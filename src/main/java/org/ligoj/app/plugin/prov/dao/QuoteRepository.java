package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.Quote;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link Quote} repository.
 */
public interface QuoteRepository extends RestRepository<Quote, Integer> {

	/**
	 * Return the quote summary.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details.
	 */
	@Query("SELECT q, COUNT(i.id), SUM(i.cpu), SUM(i.ram), SUM(s.size) FROM VmQuote AS q LEFT JOIN q.instances AS i LEFT JOIN i.storages AS s"
			+ " WHERE q.subscription = :subscription GROUP BY q")
	Object[] getSummary(int subscription);
}
