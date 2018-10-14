/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePrice} repository.
 */
public interface ProvInstancePriceRepository extends RestRepository<ProvInstancePrice, Integer> {

	/**
	 * Return all {@link ProvInstancePrice} related to given node and within a specific location.
	 *
	 * @param node
	 *            The node (provider) to match.
	 * @param location
	 *            The expected location name. Case sensitive.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<ProvInstancePrice> findAll(String node, String location);

	/**
	 * Return all licenses related to given node identifier.
	 *
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param os
	 *            The filtered OS.
	 * @return The filtered licenses.
	 */
	@CacheResult(cacheName = "prov-license")
	@Query("SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i "
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%')) AND ip.os=:os ORDER BY ip.license")
	List<String> findAllLicenses(@CacheKey String node, @CacheKey VmOs os);

	/**
	 * Return all softwares related to given node identifier.
	 *
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param os
	 *            The filtered OS.
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
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu
	 *            The minimum CPU.
	 * @param ram
	 *            The minimum RAM in MB.
	 * @param constant
	 *            The optional constant CPU behavior constraint.
	 * @param os
	 *            The requested OS.
	 * @param type
	 *            The optional instance type identifier. May be <code>null</code>.
	 * @param ephemeral
	 *            When <code>true</code>, ephemeral contract is accepted. Otherwise (<code>false</code>), only non
	 *            ephemeral instance are accepted.
	 * @param location
	 *            The requested location identifier.
	 * @param rate
	 *            Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration
	 *            The duration in month. Minimum is 1.
	 * @param license
	 *            Optional license notice. When not <code>null</code> a license constraint is added.
	 * @param software
	 *            Optional software notice. When not <code>null</code> a software constraint is added. WHen
	 *            <code>null</code>, installed software is also accepted.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip,                                               "
			+ " CASE                                                 "
			+ "  WHEN i.cpu = 0 THEN (((CEIL(:cpu) * ip.costCpu)"
			+ "                      + (CEIL(:ram / 1024.0) * ip.costRam)) * :rate * :duration)"
			+ "  WHEN t.period = 0 THEN (ip.cost * :rate * :duration)"
			+ "  ELSE (ip.costPeriod * CEIL(:duration/t.period)) END AS totalCost,"
			+ " CASE                                                 "
			+ "  WHEN i.cpu = 0 THEN (((:cpu * ip.costCpu) + (:ram * ip.costRam / 1024.0)) *:rate)"
			+ "  WHEN t.period = 0 THEN (ip.cost * :rate)            "
			+ "  ELSE ip.cost END AS monthlyCost                     "
			+ " FROM #{#entityName} ip  INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t"
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "  AND (:type IS NULL OR i.id = :type)  AND (i.cpu = 0 OR (i.cpu>= :cpu AND i.ram>=:ram))"
			+ "  AND (:os IS NULL OR ip.os=:os) AND (:constant IS NULL OR i.constant = :constant)"
			+ "  AND (:ephemeral IS TRUE OR t.ephemeral = :ephemeral)"
			+ "  AND (((:license IS NULL OR :license = 'BYOL') AND ip.license IS NULL) OR :license = ip.license)"
			+ "  AND (:software IS NULL OR :software = ip.software)   "
			+ "  AND (ip.location IS NULL OR ip.location.id = :location) ORDER BY totalCost ASC")
	List<Object[]> findLowestPrice(String node, double cpu, double ram, Boolean constant, VmOs os, Integer type,
			boolean ephemeral, int location, double rate, double duration, String license, String software,
			Pageable pageable);

}
