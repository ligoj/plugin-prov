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
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine
	 *            The filtered engine.
	 * @return The filtered licenses.
	 */
	@CacheResult(cacheName = "prov-database-license")
	@Query("SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%')) AND ip.engine=:engine ORDER BY ip.license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey String engine);

	/**
	 * Return all database editions related to given node identifier and database engine.
	 *
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine
	 *            The database engine.
	 * @return The filtered database editions.
	 */
	@CacheResult(cacheName = "prov-database-edition")
	@Query("SELECT DISTINCT(ip.edition) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "   AND ip.engine=:engine ORDER BY ip.edition")
	List<String> findAllEditions(@CacheKey String node, @CacheKey String engine);

	/**
	 * Return all database engines related to given node identifier.
	 *
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @return The filtered database engines.
	 */
	@CacheResult(cacheName = "prov-database-engine")
	@Query("SELECT DISTINCT(ip.engine) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE :node = i.node.id OR :node LIKE CONCAT(i.node.id,':%') ORDER BY ip.engine")
	List<String> findAllEngines(@CacheKey String node);

	/**
	 * Return the lowest databse instance price configuration from the minimal requirements.
	 *
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu
	 *            The minimum CPU.
	 * @param ram
	 *            The minimum RAM in MB.
	 * @param constant
	 *            The optional constant CPU behavior constraint.
	 * @param type
	 *            The optional instance type identifier. May be <code>null</code>.
	 * @param location
	 *            The requested location identifier.
	 * @param rate
	 *            Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration
	 *            The duration in month. Minimum is 1.
	 * @param license
	 *            Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param engine
	 *            Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *            <code>null</code>, installed software is also accepted.
	 * @param edition
	 *            Optional database edition.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip,                                               "
			+ " CASE                                                 "
			+ "  WHEN t.period = 0 THEN (ip.cost * :rate * :duration)"
			+ "  ELSE (ip.costPeriod * CEIL(:duration/t.period)) END AS totalCost,"
			+ " CASE                                                 "
			+ "  WHEN t.period = 0 THEN (ip.cost * :rate)            "
			+ "  ELSE ip.cost END AS monthlyCost                     "
			+ " FROM #{#entityName} ip  INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t"
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "  AND (:type IS NULL OR i.id = :type)  AND (i.cpu = 0 OR (i.cpu>= :cpu AND i.ram>=:ram))"
			+ "  AND (:edition IS NULL OR ip.edition=:edition) AND (:constant IS NULL OR i.constant = :constant)"
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND :engine = ip.engine   "
			+ "  AND (ip.location IS NULL OR ip.location.id = :location) ORDER BY totalCost ASC")
	List<Object[]> findLowestPrice(String node, double cpu, double ram, Boolean constant, Integer type, int location,
			double rate, double duration, String license, String engine, String edition, Pageable pageable);

}
