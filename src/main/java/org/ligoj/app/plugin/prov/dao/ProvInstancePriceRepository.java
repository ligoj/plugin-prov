/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePrice} repository.
 */
public interface ProvInstancePriceRepository extends BaseProvTermPriceRepository<ProvInstanceType, ProvInstancePrice> {

	/**
	 * Return all licenses related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered licenses.
	 */
	@CacheResult(cacheName = "prov-license")
	@Query("SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%')) AND ip.os=:os ORDER BY ip.license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return all softwares related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered softwares.
	 */
	@CacheResult(cacheName = "prov-software")
	@Query("SELECT DISTINCT(ip.software) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "   AND ip.os=:os AND ip.software IS NOT NULL ORDER BY ip.software")
	List<String> findAllSoftwares(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param ram         The minimum RAM in MB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software    Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("SELECT ip,                                                   "
			+ " (((CEIL(:cpu) * ip.costCpu) + (CEIL(:ram / 1024.0) * ip.costRam)) * :rate * :duration) AS totalCost,"
			+ " (((CEIL(:cpu) * ip.costCpu) + (CEIL(:ram / 1024.0) * ip.costRam)) * :rate)  AS monthlyCost          "
			+ " FROM #{#entityName} ip WHERE                             "
			+ "      (ip.location IS NULL OR ip.location.id = :location) "
			+ "  AND ip.os=:os                                           "
			+ "  AND (:software IS NULL OR :software = ip.software)      "
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)                                 "
			+ "  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)                                          "
			+ "  ORDER BY totalCost ASC")
	List<Object[]> findLowestDynamicPrice(List<Integer> types, List<Integer> terms, double cpu, double ram, VmOs os,
			int location, double rate, double duration, String license, String software, double initialCost,
			Pageable pageable);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software    Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("SELECT ip,                                                   "
			+ " CASE                                                     "
			+ "  WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)   "
			+ "  ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,"
			+ " CASE                                                     "
			+ "  WHEN ip.period = 0 THEN (ip.cost * :rate)               "
			+ "  ELSE ip.cost END AS monthlyCost                         "
			+ " FROM #{#entityName} ip  WHERE                            "
			+ "      (ip.location IS NULL OR ip.location.id = :location) "
			+ "  AND ip.os=:os                                           "
			+ "  AND (:software IS NULL OR :software = ip.software)      "
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)                                 "
			+ "  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)                                          "
			+ "  ORDER BY totalCost ASC")
	List<Object[]> findLowestPrice(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, String software, double initialCost, Pageable pageable);
}
