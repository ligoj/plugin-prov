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
	 * Return all {@link ProvInstancePrice} related to given node and within a
	 * specific location.
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
	 *            The node linked to the subscription. Is a node identifier within a
	 *            provider.
	 * @param cpu
	 *            The minimum CPU.
	 * @param ram
	 *            The minimum RAM in MB.
	 * @param constant
	 *            The optional constant CPU behavior constraint.
	 * @param os
	 *            The requested OS.
	 * @param term
	 *            The optional pricing term identifier. May be <code>null</code>.
	 * @param type
	 *            The optional instance type identifier. May be <code>null</code>.
	 * @param ephemeral
	 *            When <code>true</code>, ephemeral contract is accepted. Otherwise
	 *            (<code>false</code>), only non ephemeral instance are accepted.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip FROM #{#entityName} ip INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t LEFT JOIN ip.location AS loc"
			+ " WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ " AND (:type IS NULL OR i.id = :type) AND i.cpu>= :cpu AND i.ram>=:ram"
			+ " AND (:os IS NULL OR ip.os=:os) AND (:constant IS NULL OR i.constant = :constant)"
			+ " AND (:term IS NULL OR t.id = :term) AND i.cpu > 0 AND (:ephemeral IS TRUE OR t.ephemeral = :ephemeral)"
			+ " AND (:location IS NULL OR loc IS NULL OR loc.name = :location)                                        "
			+ " ORDER BY ip.cost ASC")
	List<ProvInstancePrice> findLowestPrice(String node, double cpu, int ram, Boolean constant, VmOs os, Integer term, Integer type,
			boolean ephemeral, String location, Pageable pageable);

	/**
	 * Return the lowest custom instance price configuration from the minimal
	 * requirements.
	 * 
	 * @param node
	 *            The requested provider node.
	 * @param constant
	 *            The optional constant CPU behavior constraint.
	 * @param os
	 *            The requested OS.
	 * @param term
	 *            The optional pricing term identifier. May be <code>null</code>.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance price or <code>null</code>.
	 */
	@Query("SELECT ip FROM #{#entityName} ip INNER JOIN FETCH ip.type AS i INNER JOIN FETCH ip.term AS t LEFT JOIN ip.location AS loc"
			+ " WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))"
			+ " AND i.cpu = 0 AND ip.os=:os AND (:constant IS NULL OR i.constant = :constant)"
			+ " AND (:term IS NULL OR t.id = :term)                                                          "
			+ " AND (:location IS NULL OR loc IS NULL OR loc.name = :location)                               "
			+ " ORDER BY ip.cost ASC           ")
	List<ProvInstancePrice> findLowestCustomPrice(String node, Boolean constant, VmOs os, Integer term, String location, Pageable pageable);

}
