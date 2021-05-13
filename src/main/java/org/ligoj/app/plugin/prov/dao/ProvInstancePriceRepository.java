/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePrice} repository.
 */
public interface ProvInstancePriceRepository
		extends BaseProvTermPriceOsRepository<ProvInstanceType, ProvInstancePrice> {

	@Override
	@CacheResult(cacheName = "prov-license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return all softwares related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered softwares.
	 */
	@CacheResult(cacheName = "prov-software")
	@Query("""
			SELECT DISTINCT(ip.software) FROM #{#entityName} ip INNER JOIN ip.type AS i
			WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))
			   AND ip.os=:os AND ip.software IS NOT NULL
			ORDER BY ip.software
			 """)
	List<String> findAllSoftwares(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param ram         The minimum RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software    Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param initialCost The maximal initial cost.
	 * @param tenancy     The requested tenancy.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("""
			SELECT ip,
			 (((CEIL(CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END /ip.incrementCpu) * ip.incrementCpu * ip.costCpu)
			 +(CASE WHEN (ip.minRam > :ram) THEN ip.minRam ELSE :ram END * ip.costRam) + ip.cost)
			 * (CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration/ip.period)) END)) AS totalCost,
			 (((CEIL(CASE WHEN (ip.minCpu > :cpu) THEN ip.minCpu ELSE :cpu END /ip.incrementCpu) * ip.incrementCpu * ip.costCpu)
			 + (:ram * ip.costRam) + ip.cost)
			 * (CASE WHEN ip.period = 0 THEN :rate ELSE 1.0 END)) AS monthlyCost
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NOT NULL
			  AND ip.os=:os
			  AND ip.tenancy=:tenancy
			  AND (:software IS NULL OR :software = ip.software)
			  AND (ip.maxCpu IS NULL or ip.maxCpu >=:cpu)
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  ORDER BY totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicPrice(List<Integer> types, List<Integer> terms, double cpu, double ram, VmOs os,
			int location, double rate, double globalRate, double duration, String license, String software,
			double initialCost, ProvTenancy tenancy, Pageable pageable);

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
	 * @param tenancy     The requested tenancy.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query("""
			SELECT ip,
			 CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)
			  ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,
			 CASE
			  WHEN ip.period = 0 THEN (ip.cost * :rate)
			  ELSE ip.cost END AS monthlyCost
			 FROM #{#entityName} ip  WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NULL
			  AND ip.os=:os
			  AND ip.tenancy=:tenancy
			  AND (:software IS NULL OR :software = ip.software)
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  ORDER BY totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestPrice(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, String software, double initialCost, ProvTenancy tenancy,
			Pageable pageable);

	/**
	 * Return all OS related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return The filtered OS.
	 */
	@CacheResult(cacheName = "prov-instance-os")
	@Query("""
			SELECT DISTINCT(ip.os) FROM #{#entityName} ip INNER JOIN ip.type AS i
			  WHERE ip.os IS NOT NULL AND (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))
			  ORDER BY ip.os
			  """)
	List<String> findAllOs(@CacheKey String node);
}
