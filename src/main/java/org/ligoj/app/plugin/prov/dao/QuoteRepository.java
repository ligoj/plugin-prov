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
	 * Return the compute quote summary from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of instances,
	 *         total RAM and total CPU.
	 */
	@Query("SELECT q, COUNT(qi.id), SUM(i.cpu), SUM(i.ram) FROM QuoteInstance AS qi INNER JOIN qi.quote AS q"
			+ " INNER JOIN qi.instance AS pi INNER JOIN pi.instance AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getComputeSummary(int subscription);

	/**
	 * Return the storage quote summary from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of storages and
	 *         total storage.
	 */
	@Query("SELECT q, COUNT(qs.id), SUM(qs.size) FROM QuoteStorage AS qs INNER JOIN qs.instance.quote AS q"
			+ " WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getStorageSummary(int subscription);
}
