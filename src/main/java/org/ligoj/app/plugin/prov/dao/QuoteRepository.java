package org.ligoj.app.plugin.prov.dao;

import java.util.List;

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
	@Query("SELECT q, COUNT(i.id) FROM Quote AS q LEFT JOIN q.instances AS qi"
			+ " LEFT JOIN qi.instance AS pi LEFT JOIN pi.instance AS i WHERE q.subscription.id = :subscription GROUP BY q")
//	@Query("SELECT q, COUNT(i.id), SUM(i.cpu), SUM(i.ram), SUM(s.size) FROM Quote AS q LEFT JOIN q.instances AS qi LEFT JOIN qi.storages AS s"
//			+ " LEFT JOIN qi.instance AS pi LEFT JOIN pi.instance AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getSummary(int subscription);
}
