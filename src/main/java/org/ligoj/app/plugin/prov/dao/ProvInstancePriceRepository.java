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
	@CacheResult(cacheName = "prov-instance-license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return all softwares related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered softwares.
	 */
	@CacheResult(cacheName = "prov-instance-software")
	@Query("""
			SELECT DISTINCT(ip.software) FROM #{#entityName} ip INNER JOIN ip.type AS i
			WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))
			   AND ip.os=:os AND ip.software IS NOT NULL
			ORDER BY ip.software
			 """)
	List<String> findAllSoftwares(@CacheKey String node, @CacheKey VmOs os);

	String LOWEST_DYNAMIC_QUERY = """
			SELECT ip,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.costGpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * CASE WHEN ip.period = 0.0 THEN :globalRate ELSE (ip.period * CEIL(:duration/ip.period)) END AS totalCost,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.costGpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * CASE WHEN ip.period = 0.0 THEN :rate ELSE 1.0 END AS monthlyCost,

			 (  ip.co2
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.co2Cpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.co2Gpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.co2Ram
			 )
			 * CASE WHEN ip.period = 0.0 THEN :globalRate ELSE (ip.period * CEIL(:duration/ip.period)) END AS totalCo2,
			 (  ip.co2
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.co2Cpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.co2Gpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.co2Ram
			 )
			 * CASE WHEN ip.period = 0 THEN :rate ELSE 1.0 END AS monthlyCo2
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NOT NULL
			  AND ip.os=:os
			  AND ip.tenancy=:tenancy
			  AND (:software  = '' OR :software = ip.software)
			  AND (ip.maxCpu  IS NULL OR ip.maxCpu >=:cpu)
			  AND (ip.maxGpu  IS NULL OR ip.maxGpu >=:gpu)
			  AND (ip.maxRam  IS NULL OR ip.maxRam >=:ram)
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  AND (ip.maxRamRatio IS NULL OR GREATEST(ip.minCpu, :cpu) * ip.maxRamRatio <= :ram)
			""";

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param gpu         The minimum GPU.
	 * @param ram         The minimum RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
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
	@Query(LOWEST_DYNAMIC_QUERY + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCost(List<Integer> types, List<Integer> terms, double cpu, double gpu, double ram,
			VmOs os, int location, double rate, double globalRate, double duration, String license, String software,
			double initialCost, ProvTenancy tenancy, Pageable pageable);

	/**
	 * Return the lowest instance CO2 configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param gpu         The minimum GPU.
	 * @param ram         The minimum RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
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
	@Query(LOWEST_DYNAMIC_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCo2(List<Integer> types, List<Integer> terms, double cpu, double gpu, double ram,
			VmOs os, int location, double rate, double globalRate, double duration, String license, String software,
			double initialCost, ProvTenancy tenancy, Pageable pageable);

	String LOWEST_QUERY = """
			SELECT ip,
			 CASE
			  WHEN ip.period = 0.0 THEN (ip.cost * :rate * :duration)
			  ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,
			 CASE
			  WHEN ip.period = 0.0 THEN (ip.cost * :rate)
			  ELSE ip.cost END AS monthlyCost,
			 CASE
			  WHEN ip.period = 0.0 THEN (ip.co2 * :rate * :duration)
			  ELSE (ip.co2Period * CEIL(:duration/ip.period)) END AS totalCo2,
			 CASE
			  WHEN ip.period = 0.0 THEN (ip.co2 * :rate)
			  ELSE ip.co2 END AS monthlyCo2
			 FROM #{#entityName} ip  WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NULL
			  AND ip.os=:os
			  AND ip.tenancy=:tenancy
			  AND (:software = '' OR :software = ip.software)
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			""";

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software    Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param initialCost The maximal initial cost.
	 * @param tenancy     The requested tenancy.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestCost(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, String software, double initialCost, ProvTenancy tenancy,
			Pageable pageable);

	/**
	 * Return the lowest instance CO2 configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software    Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param initialCost The maximal initial cost.
	 * @param tenancy     The requested tenancy.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestCo2(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, String software, double initialCost, ProvTenancy tenancy,
			Pageable pageable);

	@CacheResult(cacheName = "prov-instance-os")
	@Override
	List<String> findAllOs(@CacheKey String node);
}
