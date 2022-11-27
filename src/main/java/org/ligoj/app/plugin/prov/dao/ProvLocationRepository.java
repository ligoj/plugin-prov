/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvLocation} repository.
 */
public interface ProvLocationRepository extends RestRepository<ProvLocation, Integer> {

	/**
	 * Return all {@link ProvLocation} related to given node identifier.
	 *
	 * @param node The node identifier to match.
	 * @return All locations linked to this node.
	 */
	@Query("SELECT pl FROM ProvLocation pl INNER JOIN pl.node n WHERE"
			+ " (:node = n.id OR :node LIKE CONCAT(n.id, ':%'))"
			+ " AND EXISTS (SELECT 1 FROM ProvInstancePrice ip WHERE ip.location = pl)")
	List<ProvLocation> findAll(String node);

	/**
	 * Return the {@link ProvLocation} by its name, ignoring the case.
	 *
	 * @param node The node identifier to match.
	 * @param name The name to match.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pl FROM ProvLocation pl INNER JOIN pl.node n WHERE"
			+ " (:node = n.id OR :node LIKE CONCAT(n.id, ':%')) AND UPPER(pl.name) = UPPER(:name)")
	ProvLocation findByName(String node, String name);

	/**
	 * Return the {@link ProvLocation} identifier by its name, ignoring the case.
	 *
	 * @param node The node identifier to match.
	 * @param name The name to match.
	 *
	 * @return The entity identifier or <code>null</code>.
	 */
	@CacheResult(cacheName = "prov-location")
	@Query("SELECT pl.id FROM ProvLocation pl INNER JOIN pl.node n WHERE"
			+ " (:node = n.id OR :node LIKE CONCAT(n.id, ':%')) AND UPPER(pl.name) = UPPER(:name)")
	Integer toId(@CacheKey String node, @CacheKey String name);
	
	/**
	 * * Set all {@link ProvLocation} preferred on <code>false</code>.
	 *
	 * @param node The node identifier to match.
	 */
	@Modifying
	@Query("UPDATE ProvLocation SET preferred = false WHERE node.id = :node AND preferred = true ")
	void unsetPreferredLocation(String node);
	
	/**
	 * Set the {@link ProvLocation} preferred on <code>true</code>. to match with node and preferred location identifier
	 *
	 * @param node The node identifier to match.
	 * @param preferredId The preferred location identifier to match .
	 */
	@Modifying
	@Query("UPDATE ProvLocation SET preferred = true WHERE node.id = :node AND id = :preferredId ")
	void setPreferredLocation(String node , int preferredId );
}
