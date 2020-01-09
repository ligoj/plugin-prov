/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvDatabaseType} repository.
 */
public interface ProvDatabaseTypeRepository extends BaseProvInstanceTypeRepository<ProvDatabaseType> {
	/**
	 * Return the valid database types matching the requirements.
	 *
	 * @param node      The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu       The minimum CPU.
	 * @param ram       The minimum RAM in MB.
	 * @param constant  The optional constant CPU behavior constraint.
	 * @param type      The optional instance type identifier. May be <code>null</code>.
	 * @param processor Optional processor requirement. A <code>LIKE</code> will be used.
	 * @return The matching database instance types.
	 */
	@CacheResult(cacheName = "prov-database-type")
	@Query("SELECT id FROM #{#entityName} WHERE                          "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))"
			+ "  AND (:type IS NULL OR id = :type)                       "
			+ "  AND cpu != 0 AND cpu>= :cpu AND ram>=:ram               "
			+ "  AND (:constant IS NULL OR constant = :constant)         " + "  AND (:processor IS NULL"
			+ "   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))")
	List<Integer> findValidTypes(@CacheKey String node, @CacheKey double cpu, @CacheKey int ram,
			@CacheKey Boolean constant, @CacheKey Integer type, @CacheKey String processor);
}
