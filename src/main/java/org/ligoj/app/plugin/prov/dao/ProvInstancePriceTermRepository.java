/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePriceTerm} repository.
 */
public interface ProvInstancePriceTermRepository extends RestRepository<ProvInstancePriceTerm, Integer> {

	/**
	 * Return all {@link ProvInstancePriceTerm} related to given subscription
	 * identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The optional criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvInstancePriceTerm}.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceTerm ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(ipt.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvInstancePriceTerm> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstancePriceTerm} by it's name, ignoring the case.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceTerm ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%') AND UPPER(ipt.name) = UPPER(:name)")
	ProvInstancePriceTerm findByName(int subscription, String name);

	/**
	 * Return the {@link ProvInstancePriceTerm} by it's name for a specific
	 * provider, ignoring the case.
	 * 
	 * @param node
	 *            The provider node identifier.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("FROM ProvInstancePriceTerm WHERE node.id=:node AND UPPER(name) = UPPER(:name)")
	ProvInstancePriceTerm findByName(String node, String name);
}
