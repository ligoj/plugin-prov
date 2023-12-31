/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractTermPrice} repository.
 *
 * @param <T> The instance type's type.
 * @param <P> The price type.
 */
@SuppressWarnings("unused")
@NoRepositoryBean
public interface BaseProvTermPriceRepository<T extends AbstractInstanceType, P extends AbstractTermPrice<T>>
		extends RestRepository<P, Integer>, Co2Price {

	String LOWEST_QUERY_TERM = """
			SELECT ip,
			 (CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)
			  ELSE (ip.costPeriod * ceil(:duration/ip.period)) END) AS totalCost,
			 (CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate)
			  ELSE ip.cost END) AS monthlyCost,
			 (CASE
			  WHEN ip.period = 0 THEN (ip.co2 * :rate * :duration)
			  ELSE (ip.co2Period * ceil(:duration/ip.period)) END) AS totalCo2,
			 (CASE
			  WHEN ip.period = 0 THEN (ip.co2 * :rate)
			  ELSE ip.co2 END) AS monthlyCo2
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NULL
			  AND (ip.type.id IN :types)
			  AND (ip.term.id IN :terms)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			""";

	/**
	 * Return all {@link ProvInstancePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case-sensitive.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<P> findAll(String node, String location);

	/**
	 * Return all {@link ProvInstancePrice} related to given node, and term names, and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case-sensitive.
	 * @param term1    The expected term name prefix alternative 1.
	 * @param term2    The expected term name prefix alternative 2.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN e.term tm WHERE e.location.name = :location AND e.type.node.id = :node"
			+ " AND (tm.name LIKE CONCAT(:term1, '%') OR tm.name LIKE CONCAT(:term2, '%'))")
	List<P> findByLocation(String node, String location, final String term1, final String term2);
}
