package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstance} repository.
 */
public interface ProvInstanceRepository extends RestRepository<ProvInstance, Integer> {

	/**
	 * Return all {@link ProvInstance} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The option criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvInstance}.
	 */
	@Query("SELECT i FROM ProvInstance i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(i.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvInstance> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstance} by it's name, ignoring the case.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT i FROM ProvInstance i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%') AND UPPER(i.name) = UPPER(:name)")
	ProvInstance findByName(int subscription, String name);
}
