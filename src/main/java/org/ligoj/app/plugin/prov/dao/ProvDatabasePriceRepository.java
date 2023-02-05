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
public interface ProvDatabasePriceRepository
		extends BaseProvTermPriceVmRepository<ProvDatabaseType, ProvDatabasePrice> {

	String DYNAMIC_QUERY = DYNAMIC_QUERY_VM + """
			  AND :engine = ip.engine
			  AND (:edition = '' OR ip.edition=:edition)
			""";

	String LOWEST_QUERY = LOWEST_QUERY_VM + """
			  AND :engine = ip.engine
			  AND (:edition = '' OR ip.edition=:edition)
			""";

	/**
	 * Return all licenses related to given node identifier.
	 *
	 * @param node   The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine The filtered engine.
	 * @return The filtered licenses.
	 */
	@CacheResult(cacheName = "prov-database-license")
	@Query("SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE :node = i.node.id AND ip.engine=:engine ORDER BY ip.license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey String engine);

	/**
	 * Return all database editions related to given node identifier and database engine.
	 *
	 * @param node   The node linked to the subscription. Is a node identifier within a provider.
	 * @param engine The database engine.
	 * @return The filtered database editions.
	 */
	@CacheResult(cacheName = "prov-database-edition")
	@Query("SELECT DISTINCT(ip.edition) FROM #{#entityName} ip INNER JOIN ip.type AS i " + "  WHERE :node = i.node.id"
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
			+ "  WHERE :node = i.node.id ORDER BY ip.engine")
	List<String> findAllEngines(@CacheKey String node);

	/**
	 * Return the lowest database price configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param gpu         The minimum GPU.
	 * @param ram         The minimum RAM in GiB.
	 * @param engine      Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param edition     Optional database edition.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The cheapest database price or empty result.
	 */
	@Query(DYNAMIC_QUERY + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCost(List<Integer> types, List<Integer> terms, double cpu,double cpu2, double gpu, double gpu2, double ram,double ram2,
			String engine, String edition, int location, double rate, double globalRate, double duration,
			String license, double initialCost, Pageable pageable);

	/**
	 * Return the lowest database CP2 configuration from the minimal requirements.
	 *
	 * @param types       The valid instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param cpu         The minimum CPU.
	 * @param gpu         The minimum GPU.
	 * @param ram         The minimum RAM in GiB.
	 * @param engine      Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param edition     Optional database edition.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param globalRate  Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The cheapest database price or empty result.
	 */
	@Query(DYNAMIC_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCo2(List<Integer> types, List<Integer> terms, double cpu, double cpu2, double gpu,double gpu2, double ram, double ram2,
			String engine, String edition, int location, double rate, double globalRate, double duration,
			String license, double initialCost, Pageable pageable);

	/**
	 * Return the lowest database instance price configuration from the minimal requirements.
	 *
	 * @param types       The required instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param engine      Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param edition     Optional database edition.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCost(List<Integer> types, List<Integer> terms, int location, double rate, double duration,
			String license, String engine, String edition, double initialCost, Pageable pageable);

	/**
	 * Return the lowest database instance CO2 configuration from the minimal requirements.
	 *
	 * @param types       The required instance type identifiers.
	 * @param terms       The valid instance terms identifiers.
	 * @param location    The requested location identifier.
	 * @param rate        Usage rate within the duration, positive number, from <code>0.01</code> (stopped) to
	 *                    <code>1</code>, (full time).
	 * @param duration    The duration in month. Minimum is 1.
	 * @param license     Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param engine      Database engine notice. When not <code>null</code> a software constraint is added. WHen
	 *                    <code>null</code>, installed software is also accepted.
	 * @param edition     Optional database edition.
	 * @param initialCost The maximal initial cost.
	 * @param pageable    The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCo2(List<Integer> types, List<Integer> terms, int location, double rate, double duration,
			String license, String engine, String edition, double initialCost, Pageable pageable);

}
