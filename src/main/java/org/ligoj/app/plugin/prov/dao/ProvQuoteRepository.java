package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuote} repository.
 */
public interface ProvQuoteRepository extends RestRepository<ProvQuote, Integer> {

	/**
	 * Return the compute quote summary from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of instances,
	 *         total RAM and total CPU.
	 */
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(qi.cpu*qi.minQuantity),0), COALESCE(SUM(qi.ram*qi.minQuantity),0),"
			+ " COALESCE(SUM(CASE qi.internet WHEN 0 THEN qi.minQuantity ELSE 0 END),0)"
			+ " FROM ProvQuote q LEFT JOIN q.instances AS qi"
			+ " LEFT JOIN qi.instancePrice AS ip LEFT JOIN ip.instance AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getComputeSummary(int subscription);

	/**
	 * Return the storage quote summary from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of storages and
	 *         total storage.
	 */
	@Query("SELECT q, COALESCE(COUNT(qs.id),0), COALESCE(SUM(qs.size * COALESCE(qi.minQuantity,1)),0) FROM ProvQuote q LEFT JOIN q.storages AS qs"
			+ " LEFT JOIN qs.quoteInstance AS qi WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getStorageSummary(int subscription);

	/**
	 * Return the compute quote details from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The compute quote details : Quote, instance details and price
	 *         details.
	 */
	@Query("FROM ProvQuote AS q LEFT JOIN FETCH q.instances AS qi LEFT JOIN FETCH qi.instancePrice AS ip "
			+ " LEFT JOIN FETCH ip.instance AS i LEFT JOIN FETCH ip.type WHERE q.subscription.id = :subscription")
	ProvQuote getCompute(int subscription);

	/**
	 * Return the storage quote details from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The storage quote details with the optional linked instance.
	 */
	@Query("FROM ProvQuoteStorage AS qs INNER JOIN FETCH qs.type LEFT JOIN FETCH qs.quoteInstance"
			+ " WHERE qs.configuration.subscription.id = :subscription")
	List<ProvQuoteStorage> getStorage(int subscription);
}
