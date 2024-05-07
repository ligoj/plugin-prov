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

	String DYNAMIC_QUERY = DYNAMIC_QUERY_OS + """
			  AND ip.tenancy=:tenancy
			  AND (:software  = '' OR :software = ip.software)
			""";

	String LOWEST_QUERY = LOWEST_QUERY_OS + """
			  AND ip.tenancy=:tenancy
			  AND (:software = '' OR :software = ip.software)
			""";

	@Override
	@CacheResult(cacheName = "prov-instance-license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return all software related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered software.
	 */
	@CacheResult(cacheName = "prov-instance-software")
	@Query("""
			SELECT DISTINCT(ip.software) FROM ProvInstancePrice ip INNER JOIN ip.type AS i
			WHERE :node = i.node.id
			   AND ip.os=:os AND ip.software IS NOT NULL
			ORDER BY ip.software
			""")
	List<String> findAllSoftwareNames(@CacheKey String node, @CacheKey VmOs os);

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
	@Query(DYNAMIC_QUERY + """
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
	@Query(DYNAMIC_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCo2(List<Integer> types, List<Integer> terms, double cpu, double gpu, double ram,
			VmOs os, int location, double rate, double globalRate, double duration, String license, String software,
			double initialCost, ProvTenancy tenancy, Pageable pageable);

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
