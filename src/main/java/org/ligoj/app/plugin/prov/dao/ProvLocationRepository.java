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
	 * @param node
	 *            The node identifier to match.
	 * @param criteria
	 *            The optional criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvLocation}.
	 */
	@Query("SELECT pl FROM ProvLocation pl INNER JOIN pl.node n WHERE"
			+ " (:node = n.id OR :node LIKE CONCAT(n.id, ':%'))"
			+ " AND (:criteria IS NULL                                              "
			+ "   OR UPPER(pl.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%')"
			+ "   OR UPPER(pl.description) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%')"
			+ "   OR UPPER(pl.subRegion) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))"
			+ " AND EXISTS (SELECT 1 FROM ProvInstancePrice ip WHERE ip.location = pl)")
	Page<ProvLocation> findAll(String node, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvLocation} by it's name, ignoring the case.
	 * 
	 * @param node
	 *            The node identifier to match.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pl FROM ProvLocation pl INNER JOIN pl.node n WHERE"
			+ " (:node = n.id OR :node LIKE CONCAT(n.id, ':%')) AND UPPER(pl.name) = UPPER(:name)")
	ProvLocation findByName(String node, String name);
}
