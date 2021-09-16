/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuote} repository.
 */
public interface ProvQuoteRepository extends RestRepository<ProvQuote, Integer> {

	/**
	 * Return the compute quote summary from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of instances, total RAM and total CPU and total GPU.
	 */
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(qi.cpu*qi.minQuantity),0), COALESCE(SUM(qi.gpu*qi.minQuantity),0), COALESCE(SUM(qi.ram*qi.minQuantity),0),"
			+ " COALESCE(SUM(CASE qi.internet WHEN 0 THEN qi.minQuantity ELSE 0 END),0) FROM ProvQuote q LEFT JOIN q.instances AS qi"
			+ " LEFT JOIN qi.price AS ip LEFT JOIN ip.type AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getComputeSummary(int subscription);

	/**
	 * Return the database quote summary from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of databases, total RAM and total CPU.
	 */
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(qi.cpu*qi.minQuantity),0), COALESCE(SUM(qi.ram*qi.minQuantity),0),"
			+ " COALESCE(SUM(CASE qi.internet WHEN 0 THEN qi.minQuantity ELSE 0 END),0) FROM ProvQuote q LEFT JOIN q.databases AS qi"
			+ " LEFT JOIN qi.price AS ip LEFT JOIN ip.type AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getDatabaseSummary(int subscription);

	/**
	 * Return the container quote summary from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of containers, total RAM and total CPU.
	 */
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(qi.cpu*qi.minQuantity),0), COALESCE(SUM(qi.ram*qi.minQuantity),0),"
			+ " COALESCE(SUM(CASE qi.internet WHEN 0 THEN qi.minQuantity ELSE 0 END),0) FROM ProvQuote q LEFT JOIN q.containers AS qi"
			+ " LEFT JOIN qi.price AS ip LEFT JOIN ip.type AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getContainerSummary(int subscription);

	/**
	 * Return the function quote summary from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of function, total RAM and total CPU.
	 */
	@Query("SELECT q, COALESCE(COUNT(qi.id),0), COALESCE(SUM(qi.nbRequests),0) FROM ProvQuote q LEFT JOIN q.functions AS qi"
			+ " LEFT JOIN qi.price AS ip LEFT JOIN ip.type AS i WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getFunctionSummary(int subscription);

	/**
	 * Return the storage quote summary from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The quote with aggregated details : Quote, amount of storages and total storage.
	 */
	@Query("SELECT q, COALESCE(SUM(CASE WHEN qs.id IS NULL THEN 0 ELSE COALESCE(qi.minQuantity,1) END),0),"
			+ " COALESCE(SUM(qs.size*COALESCE(qi.minQuantity,1)),0) FROM ProvQuote q LEFT JOIN q.storages AS qs"
			+ " LEFT JOIN qs.quoteInstance AS qi  LEFT JOIN qs.quoteContainer AS qc WHERE q.subscription.id = :subscription GROUP BY q")
	List<Object[]> getStorageSummary(int subscription);

	/**
	 * Return the compute quote details from the related subscription.
	 *
	 * @param subscription The subscription identifier linking the quote.
	 * @return The compute quote details : Quote, instance details and price details.
	 */
	@Query("FROM #{#entityName} AS q LEFT JOIN FETCH q.instances AS qi LEFT JOIN FETCH qi.price AS ip "
			+ " LEFT JOIN FETCH ip.type AS i LEFT JOIN FETCH ip.term LEFT JOIN FETCH q.usage WHERE q.subscription.id = :subscription")
	ProvQuote getCompute(int subscription);

	/**
	 * Return the amount of quotes based on the related node.
	 *
	 * @param node The node identifier. Sub nodes are also involved.
	 * @return The amount of quotes based on the related node.
	 */
	@Query("SELECT COUNT (q) FROM #{#entityName} AS q INNER JOIN q.subscription AS s WHERE s.node.id = :node OR s.node.id LIKE CONCAT(:node, ':%')")
	long countByNode(String node);
}
