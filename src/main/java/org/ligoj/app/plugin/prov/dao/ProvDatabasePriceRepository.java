/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvDatabasePrice} repository.
 */
public interface ProvDatabasePriceRepository extends BaseProvTermPriceRepository<ProvDatabaseType, ProvDatabasePrice> {

	/**
	 * Return all licenses related to given node identifier.
	 *
	 * @param node   The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine The filtered engine.
	 * @return The filtered licenses.
	 */
	@CacheResult(cacheName = "prov-database-license")
	@Query("SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%')) AND ip.engine=:engine ORDER BY ip.license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey String engine);

	/**
	 * Return all database editions related to given node identifier and database engine.
	 *
	 * @param node   The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine The database engine.
	 * @return The filtered database editions.
	 */
	@CacheResult(cacheName = "prov-database-edition")
	@Query("SELECT DISTINCT(ip.edition) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "   AND ip.engine=:engine AND ip.edition IS NOT NULL ORDER BY ip.edition")
	List<String> findAllEditions(@CacheKey String node, @CacheKey String engine);

	/**
	 * Return all database engines related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return The filtered database engines.
	 */
	@CacheResult(cacheName = "prov-database-engine")
	@Query("SELECT DISTINCT(ip.engine) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE :node = i.node.id OR :node LIKE CONCAT(i.node.id,':%') ORDER BY ip.engine")
	List<String> findAllEngines(@CacheKey String node);

	/**
	 * Return the lowest database price configuration from the minimal requirements.
	 *
	 * @param types    The valid instance type identifiers.
	 * @param terms    The valid instance terms identifiers.
	 * @param cpu      The minimum CPU.
	 * @param ram      The minimum RAM in MB.
	 * @param engine   Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                 <code>null</code>, installed software is also accepted.
	 * @param edition  Optional database edition.
	 * @param location The requested location identifier.
	 * @param rate     Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration The duration in month. Minimum is 1.
	 * @param license  Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param pageable The page control to return few item.
	 * @return The minimum database price or empty result.
	 */
	@Query("SELECT ip,                                               "
			+ " (((CEIL(:cpu) * ip.costCpu) + (CEIL(:ram / 1024.0) * ip.costRam)) * :rate * :duration) AS totalCost,"
			+ " (((CEIL(:cpu) * ip.costCpu) + (CEIL(:ram / 1024.0) * ip.costRam)) * :rate)  AS monthlyCost          "
			+ " FROM #{#entityName} ip                               "
			+ "  WHERE (ip.type.id IN :types) AND (ip.term.id IN :terms)   "
			+ "  AND :engine = ip.engine                             "
			+ "  AND (:edition IS NULL OR ip.edition=:edition)       "
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND (ip.location IS NULL OR ip.location.id = :location) ORDER BY totalCost ASC")
	List<Object[]> findLowestDynamicPrice(List<Integer> types, List<Integer> terms, double cpu, double ram,
			String engine, String edition, int location, double rate, double duration, String license,
			Pageable pageable);

	/**
	 * Return the lowest database instance price configuration from the minimal requirements.
	 *
	 * @param types    The required instance type identifiers.
	 * @param location The requested location identifier.
	 * @param rate     Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration The duration in month. Minimum is 1.
	 * @param license  Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param engine   Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                 <code>null</code>, installed software is also accepted.
	 * @param edition  Optional database edition.
	 * @param pageable The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("SELECT ip,                                               "
			+ " CASE                                                 "
			+ "  WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)"
			+ "  ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,"
			+ " CASE                                                 "
			+ "  WHEN ip.period = 0 THEN (ip.cost * :rate)            "
			+ "  ELSE ip.cost END AS monthlyCost                     "
			+ " FROM #{#entityName} ip                               "
			+ "  WHERE (ip.type.id IN :types)                        "
			+ "  AND :engine = ip.engine                             "
			+ "  AND (:edition IS NULL OR ip.edition=:edition)       "
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND ip.location.id = :location ORDER BY totalCost ASC")
	List<Object[]> findLowestPrice(List<Integer> types, int location, double rate, double duration, String license,
			String engine, String edition, Pageable pageable);

}
