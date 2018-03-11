package org.ligoj.app.plugin.prov.dao;

import java.util.List;

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
	 *            Optional location name. May be <code>null</code>.
	 * @param rate
	 *            Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration
	 *            The duration in month. Minimum is 1.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip,                                               "
			+ " CASE                                                 "
			+ "  WHEN t.period = 0 THEN (ip.cost * :rate * :duration)"
			+ "  WHEN :duration <= t.period THEN ip.costPeriod       "
			+ "  WHEN MOD (:duration, t.period) = 0 THEN ((:duration/t.period) * ip.costPeriod)"
			+ "  ELSE (((:duration/t.period) +1) * ip.costPeriod) END AS totalCost,"
			+ " CASE WHEN t.period = 0 THEN (ip.cost * :rate) ELSE ip.cost END AS monthlyCost"
			+ " FROM #{#entityName} ip"
			+ "  INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t LEFT JOIN ip.location AS loc"
			+ "  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ "  AND (:type IS NULL OR i.id = :type) AND i.cpu>= :cpu AND i.ram>=:ram"
			+ "  AND (:os IS NULL OR ip.os=:os) AND (:constant IS NULL OR i.constant = :constant)"
			+ "  AND i.cpu > 0 AND (:ephemeral IS TRUE OR t.ephemeral = :ephemeral)"
			+ "  AND (:location IS NULL OR loc IS NULL OR loc.name = :location)                                        "
			+ " ORDER BY totalCost ASC")
	List<Object[]> findLowestPrice(String node, double cpu, int ram, Boolean constant, VmOs os, Integer type,
			boolean ephemeral, String location, double rate, double duration, Pageable pageable);

	/**
	 * Return the lowest custom instance price configuration from the minimal requirements.
	 * 
	 * @param node
	 *            The requested provider node.
	 * @param cpu
	 *            The minimum CPU.
	 * @param ram
	 *            The minimum RAM in MB.
	 * @param constant
	 *            The optional constant CPU behavior constraint.
	 * @param os
	 *            The requested OS.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip, (:cpu * ip.costCpu + :ram * ip.costRam) AS monthlyCost FROM #{#entityName} ip"
			+ " INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t LEFT JOIN ip.location AS loc"
			+ " WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ " AND i.cpu = 0 AND ip.os=:os AND (:constant IS NULL OR i.constant = :constant)"
			+ " AND (:location IS NULL OR loc IS NULL OR loc.name = :location)                               "
			+ " ORDER BY monthlyCost ASC           ")
	List<Object[]> findLowestCustomPrice(String node, double cpu, double ram, Boolean constant, VmOs os,
			String location, Pageable pageable);

}
