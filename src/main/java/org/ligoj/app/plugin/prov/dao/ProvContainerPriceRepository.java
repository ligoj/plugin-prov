/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvContainerPrice} repository.
 */
public interface ProvContainerPriceRepository
		extends BaseProvTermPriceOsRepository<ProvContainerType, ProvContainerPrice> {

	@Override
	@CacheResult(cacheName = "prov-container-license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	@Override
	@CacheResult(cacheName = "prov-container-os")
	List<String> findAllOs(@CacheKey String node);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The required CPU.
	 * @param ram         The required RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The cheapest container price or empty result.
	 */
	@Query("""
			SELECT ip,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration/ip.period)) END AS totalCost,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * CASE WHEN ip.period = 0 THEN :rate ELSE 1.0 END AS monthlyCost
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NOT NULL
			  AND ip.os=:os
			  AND (ip.maxCpu IS NULL OR ip.maxCpu >=:cpu)
			  AND (ip.maxRam IS NULL OR ip.maxRam >=:ram)
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  AND (ip.maxRamRatio IS NULL OR GREATEST(ip.minCpu, :cpu) * ip.maxRamRatio <= :ram)
			  ORDER BY totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicPrice(List<Integer> types, List<Integer> terms, double cpu, double ram, VmOs os,
			int location, double rate, double globalRate, double duration, String license, double initialCost,
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
	 * @param initialCost The maximal initial cost.
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
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  ORDER BY totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestPrice(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, double initialCost, Pageable pageable);

}
