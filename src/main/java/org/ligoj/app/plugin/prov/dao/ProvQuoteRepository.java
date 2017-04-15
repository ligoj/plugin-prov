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
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(i.cpu),0), COALESCE(SUM(i.ram),0) FROM ProvQuote q LEFT JOIN q.instances AS qi"
			+ " LEFT JOIN qi.instance AS pi LEFT JOIN pi.instance AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getComputeSummary(int subscription);

	/**
	 * Return the storage quote summary from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of storages and
	 *         total storage.
	 */
	@Query("SELECT q, COALESCE(COUNT(qs.id),0), COALESCE(SUM(qs.size),0) FROM ProvQuote q LEFT JOIN q.storages AS qs"
			+ " WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getStorageSummary(int subscription);

	/**
	 * Return the compute quote details from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The compute quote details : Quote, instance details and price details.
	 */
	@Query("SELECT q FROM ProvQuote AS q LEFT JOIN FETCH q.instances AS qi INNER JOIN FETCH qi.instance AS pi "
			+ " LEFT JOIN FETCH pi.instance AS i LEFT JOIN FETCH pi.type WHERE q.subscription.id = :subscription")
	ProvQuote getCompute(int subscription);

	/**
	 * Return the storage quote details from the related subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier linking the quote.
	 * @return The storage quote details with the optional linked instance.
	 */
	@Query("SELECT qs FROM ProvQuoteStorage AS qs INNER JOIN FETCH qs.storage LEFT JOIN FETCH qs.instance"
			+ " WHERE qs.quote.subscription.id = :subscription")
	List<ProvQuoteStorage> getStorage(int subscription);
}
