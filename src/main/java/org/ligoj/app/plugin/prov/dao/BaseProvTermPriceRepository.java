/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link ProvDatabasePrice} repository.
 * 
 * @param <T> The instance type type.
 * @param <P> The price type.
 */
@NoRepositoryBean
public interface BaseProvTermPriceRepository<T extends AbstractInstanceType, P extends AbstractTermPrice<T>>
		extends RestRepository<P, Integer> {

	/**
	 * Return all {@link ProvInstancePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case sensitive.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<P> findAll(String node, String location);

	/**
	 * Return all {@link ProvInstancePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case sensitive.
	 * @param term1    The expected term name prefix alternative 1.
	 * @param term2    The expected term name prefix alternative 2.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN e.term tm WHERE e.location.name = :location AND e.type.node.id = :node"
			+ " AND (tm.name LIKE CONCAT(:term1, '%') OR tm.name LIKE CONCAT(:term2, '%'))")
	List<P> findByLocation(String node, String location, final String term1, final String term2);

}
