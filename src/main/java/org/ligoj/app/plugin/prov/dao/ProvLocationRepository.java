package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvLocation} repository.
 */
public interface ProvLocationRepository extends RestRepository<ProvLocation, Integer> {

	/**
	 * Return all {@link ProvLocation} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The optional criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvLocation}.
	 */
	@Query("SELECT pl FROM ProvLocation pl, Subscription s INNER JOIN s.node AS sn INNER JOIN pl.node AS pln"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(pln.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(pl.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvLocation> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvLocation} by its identifier and also valid for the
	 * given subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param id
	 *            The entity's identifier to match.
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pl FROM ProvLocation pl, Subscription s INNER JOIN s.node AS sn INNER JOIN pl.node AS pln"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(pln.id, ':%') AND pl.id = :id")
	ProvLocation findById(int subscription, Integer id);

	/**
	 * Return the {@link ProvLocation} by it's name, ignoring the case.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pl FROM ProvLocation pl, Subscription s INNER JOIN s.node AS sn INNER JOIN pl.node AS pln"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(pln.id, ':%') AND UPPER(pl.name) = UPPER(:name)")
	ProvLocation findByName(int subscription, String name);
}
