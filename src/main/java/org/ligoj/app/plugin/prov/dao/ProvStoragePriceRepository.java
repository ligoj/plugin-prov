/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvStoragePrice} repository.
 */
public interface ProvStoragePriceRepository extends RestRepository<ProvStoragePrice, Integer> {

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<ProvStoragePrice> findAll(String node, String location);

	/**
	 * Return the cheapest storage configuration from the minimal requirements.
	 *
	 * @param node      The node linked to the subscription. Is a node identifier within a provider.
	 * @param size      The requested size in GB.
	 * @param latency   The optional requested latency. May be <code>null</code>.
	 * @param instance  The optional requested quote instance identifier to be associated. The related instance must be
	 *                  in the same provider.
	 * @param database  The optional requested quote database identifier to be associated. The related database must be
	 *                  in the same provider. When <code>null</code>, only database storage compatible is excluded.
	 * @param container The optional requested quote container identifier to be associated. The related container must
	 *                  be in the same provider.
	 * @param optimized The optional requested optimized. May be <code>null</code>.
	 * @param location  The expected location identifier.
	 * @param qLocation The default location identifier.
	 * @param pageable  The page control to return few item.
	 * @return The cheapest storage or <code>null</code>. The first item corresponds to the storage price, the second is
	 *         the computed price.
	 */
	@Query("SELECT sp, " + " (sp.cost + (CASE WHEN :size < st.minimal THEN st.minimal                        "
			+ "                  WHEN st.increment IS NULL THEN :size                           "
			+ "                  ELSE (CEIL(:size / st.increment)*st.increment)                 "
			+ "             END) * sp.costGb) AS cost,                                          "
			+ " st.latency AS latency FROM #{#entityName} AS sp INNER JOIN sp.type st           "
			+ " WHERE (:node = st.node.id OR :node LIKE CONCAT(st.node.id,'%'))                 "
			+ " AND (:latency IS NULL OR st.latency >= :latency)                                "
			+ " AND (:optimized IS NULL OR st.optimized = :optimized)                           "
			+ " AND (st.maximal IS NULL OR st.maximal >= :size)                                 "
			+ " AND (sp.location IS NULL OR sp.location.id = :location)                         "
			+ " AND (:instance IS NULL OR st.notInstanceType IS NULL                            "
			+ "   OR EXISTS(SELECT 1 FROM ProvQuoteInstance qi                                  "
			+ "        LEFT JOIN qi.price pqi LEFT JOIN pqi.type type                           "
			+ "     WHERE qi.id = :instance                                                     "
			+ "      AND (sp.location = qi.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code NOT LIKE st.notInstanceType)))                              "
			+ " AND (:instance IS NULL OR (st.instanceType IS NOT NULL                          "
			+ "   AND EXISTS(SELECT 1 FROM ProvQuoteInstance qi                                 "
			+ "        LEFT JOIN qi.price pqi LEFT JOIN pqi.type type                           "
			+ "     WHERE qi.id = :instance                                                     "
			+ "      AND (sp.location = qi.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code LIKE st.instanceType))))                                    "
			+ " AND (:container IS NULL OR st.notContainerType IS NULL                           "
			+ "   OR EXISTS(SELECT 1 FROM ProvQuoteContainer qc                                 "
			+ "        LEFT JOIN qc.price pqc LEFT JOIN pqc.type type                           "
			+ "     WHERE qc.id = :container                                                     "
			+ "      AND (sp.location = qc.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code NOT LIKE st.notContainerType)))                             "
			+ " AND (:container IS NULL OR (st.containerType IS NOT NULL                        "
			+ "   AND EXISTS(SELECT 1 FROM ProvQuoteContainer qc                                "
			+ "        LEFT JOIN qc.price pqc LEFT JOIN pqc.type type                           "
			+ "     WHERE qc.id = :container                                                    "
			+ "      AND (sp.location = qc.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code LIKE st.containerType))))                                   "
			+ " AND (:database IS NULL OR st.notDatabaseType IS NULL                            "
			+ "    OR EXISTS(SELECT 1 FROM ProvQuoteDatabase qb                                 "
			+ "        LEFT JOIN qb.price price                                                 "
			+ "        LEFT JOIN qb.price pqb LEFT JOIN pqb.type type                           "
			+ "     WHERE qb.id = :database                                                     "
			+ "      AND (sp.location = qb.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code NOT LIKE st.notDatabaseType)                                "
			+ "      AND ((price.storageEngine IS NULL AND st.engine IS NULL)                   "
			+ "        OR price.storageEngine = st.engine)))                                    "
			+ " AND (:database IS NULL OR (st.databaseType IS NOT NULL                          "
			+ "    AND EXISTS(SELECT 1 FROM ProvQuoteDatabase qb                                "
			+ "        LEFT JOIN qb.price price                                                 "
			+ "        LEFT JOIN qb.price pqb LEFT JOIN pqb.type type                           "
			+ "     WHERE qb.id = :database                                                     "
			+ "      AND (sp.location = qb.location OR sp.location.id = :qLocation)             "
			+ "      AND (type.code LIKE st.databaseType)                                       "
			+ "      AND ((price.storageEngine IS NULL AND st.engine IS NULL) OR price.storageEngine = st.engine))))"
			+ " ORDER BY cost ASC, latency DESC")
	List<Object[]> findLowestPrice(String node, double size, Rate latency, Integer instance, Integer database,
			Integer container, ProvStorageOptimized optimized, int location, int qLocation, Pageable pageable);

	/**
	 * Return the {@link ProvStoragePrice} by it's name and the location and related to given subscription.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param type         The type's code to match. Case sensitive.
	 * @param location     The expected location identifier.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT sp FROM #{#entityName} sp, Subscription s INNER JOIN s.node AS sn LEFT JOIN sp.location AS loc INNER JOIN sp.type AS st"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(st.node.id, ':%') AND st.code = :type "
			+ " AND (loc IS NULL OR :location IS NULL OR loc.id = :location)")
	ProvStoragePrice findByTypeCode(int subscription, String type, Integer location);

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN FETCH e.type t INNER JOIN e.location l WHERE                      "
			+ " (:location IS NULL OR l.name = :location) AND t.node.id = :node             ")
	List<ProvStoragePrice> findByLocation(String node, String location);

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node The node (provider) to match.
	 * @param type The expected type code. Case sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN FETCH e.type t INNER JOIN FETCH e.location l WHERE                      "
			+ " t.code = :type AND t.node.id = :node             ")
	List<ProvStoragePrice> findByTypeName(String node, String type);
}
