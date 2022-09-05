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
	 * @param gpu         The required CPU.
	 * @param ram         The required RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The cheapest container price or empty result.
	 */
	@Query(DYNAMIC_QUERY_OS + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCost(List<Integer> types, List<Integer> terms, double cpu, double gpu, double ram,
			VmOs os, int location, double rate, double globalRate, double duration, String license, double initialCost,
			Pageable pageable);

	/**
	 * Return the lowest instance CO2 configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The required CPU.
	 * @param gpu         The required CPU.
	 * @param ram         The required RAM in GiB.
	 * @param os          The requested OS.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The cheapest container price or empty result.
	 */
	@Query(DYNAMIC_QUERY_OS + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCo2(List<Integer> types, List<Integer> terms, double cpu, double gpu, double ram,
			VmOs os, int location, double rate, double globalRate, double duration, String license, double initialCost,
			Pageable pageable);
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
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY_OS + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCost(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, double initialCost, Pageable pageable);

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
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY_OS + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCo2(List<Integer> types, List<Integer> terms, VmOs os, int location, double rate,
			double duration, String license, double initialCost, Pageable pageable);

}
