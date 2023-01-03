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
@SuppressWarnings("unused")
public interface ProvStoragePriceRepository extends RestRepository<ProvStoragePrice, Integer> {

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case-sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<ProvStoragePrice> findAll(String node, String location);

	/**
	 * Return the cheapest storage configuration from the minimal requirements.
	 *
	 * @param node                  The node linked to the subscription. Is a node identifier within a provider.
	 * @param size                  The requested size in GB.
	 * @param latency               The optional requested latency. May be <code>null</code>.
	 * @param instanceType          The optional requested quote instance type's code to be associated. The related
	 *                              instance must be in the same location.
	 * @param databaseType          The optional requested quote database type's code to be associated. The related
	 *                              database must be in the same provider. When <code>null</code>, only database storage
	 *                              compatible is excluded.
	 * @param databaseStorageEngine The optional requested quote database storage type to be associated. The related
	 *                              database must be in the same location.
	 * @param containerType         The optional requested quote container type's code to be associated. The related
	 *                              container must be in the same location.
	 * @param functionType          The optional requested quote function type's code to be associated. The related
	 *                              function must be in the same location.
	 * @param optimized             The optional requested optimized. May be <code>null</code>.
	 * @param location              The expected location identifier.
	 * @param qLocation             The default location identifier.
	 * @param pageable              The page control to return few item.
	 * @return The cheapest storage or <code>null</code>. The first item corresponds to the storage price, the second is
	 *         the computed price.
	 */
	@Query("""
			SELECT sp,
			 (sp.cost + (CASE WHEN :size < st.minimal THEN st.minimal
			                  WHEN st.increment IS NULL THEN :size
			                  ELSE (CEIL(:size / st.increment)*st.increment)
			             END) * sp.costGb) AS cost,
			 st.latency AS latency,
			 st.code AS code
			 FROM #{#entityName} AS sp INNER JOIN sp.type st
			 WHERE (:node = st.node.id OR :node LIKE CONCAT(st.node.id,'%'))
			 AND (sp.location.id = :location AND (:qLocation = 0 OR (sp.location.id = :qLocation)))
			 AND (st.latency >= :latency)
			 AND (CAST(:optimized as string) IS NULL OR st.optimized = :optimized)
			 AND (st.maximal IS NULL OR st.maximal >= :size)
			 AND (:instanceType = ''
			 	OR (  (st.notInstanceType IS NULL OR :instanceType NOT LIKE st.notInstanceType)
			 	  AND (st.instanceType IS NOT NULL AND :instanceType LIKE st.instanceType)))
			 AND (:containerType = ''
			 	OR (  (st.notContainerType IS NULL OR :containerType NOT LIKE st.notContainerType)
			 	  AND (st.containerType IS NOT NULL AND :containerType LIKE st.containerType)))
			 AND (:functionType = ''
			 	OR (  (st.notFunctionType IS NULL OR :functionType NOT LIKE st.notFunctionType)
			 	  AND (st.functionType IS NOT NULL AND :functionType LIKE st.functionType)))
			 AND (:databaseType = ''
			 	OR (  (st.notDatabaseType IS NULL OR :databaseType NOT LIKE st.notDatabaseType)
			 	  AND (st.databaseType IS NOT NULL AND :databaseType LIKE st.databaseType)
			 	  AND ((st.engine IS NULL AND :databaseStorageEngine = '') OR (st.engine = :databaseStorageEngine))))
			 ORDER BY cost ASC, latency DESC, code""")
	List<Object[]> findLowestPrice(String node, double size, Rate latency, String instanceType, String databaseType,
			String databaseStorageEngine, String containerType, String functionType, ProvStorageOptimized optimized,
			int location, int qLocation, Pageable pageable);

	/**
	 * Return the {@link ProvStoragePrice} by its name and the location and related to given subscription.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param type         The type's code to match. Case-sensitive.
	 * @param location     The expected location identifier.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT sp FROM #{#entityName} sp, Subscription s INNER JOIN s.node AS sn INNER JOIN sp.location AS loc INNER JOIN sp.type AS st"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(st.node.id, ':%') AND st.code = :type "
			+ " AND loc.id = :location")
	ProvStoragePrice findByTypeCode(int subscription, String type, int location);

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node     The node (provider) to match.
	 * @param location The expected location name. Case-sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN FETCH e.type t INNER JOIN e.location l WHERE                      "
			+ " (:location = '' OR l.name = :location) AND t.node.id = :node             ")
	List<ProvStoragePrice> findByLocation(String node, String location);

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a specific location.
	 *
	 * @param node The node (provider) to match.
	 * @param type The expected type code. Case-sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} e INNER JOIN FETCH e.type t INNER JOIN FETCH e.location l WHERE                      "
			+ " t.code = :type AND t.node.id = :node             ")
	List<ProvStoragePrice> findByTypeName(String node, String type);
}
