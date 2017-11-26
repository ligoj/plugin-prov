package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvStoragePrice} repository.
 */
public interface ProvStoragePriceRepository extends RestRepository<ProvStoragePrice, Integer> {

	/**
	 * Return all {@link ProvStoragePrice} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param location
	 *            The expected location name. Case insensitive.
	 * @param criteria
	 *            The option criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("SELECT sp FROM #{#entityName} sp, Subscription s INNER JOIN s.node AS sn INNER JOIN sp.type AS st INNER JOIN st.node AS stn"
			+ " LEFT JOIN sp.location AS loc                 "
			+ " WHERE (loc IS NULL OR :location IS NULL OR UPPER(loc.name) = UPPER(:location))"
			+ " AND s.id = :subscription AND sn.id LIKE CONCAT(stn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(st.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvStoragePrice> findAll(int subscription, String location, String criteria, Pageable pageRequest);

	/**
	 * Return all {@link ProvStoragePrice} related to given node and within a
	 * specific location.
	 * 
	 * @param node
	 *            The node (provider) to match.
	 * @param location
	 *            The expected location name. Case sensitive.
	 * @return The filtered {@link ProvStoragePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<ProvStoragePrice> findAll(String node, String location);

	/**
	 * Return the cheapest storage configuration from the minimal requirements.
	 * 
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a
	 *            provider.
	 * @param size
	 *            The requested size in GB.
	 * @param frequency
	 *            The optional requested frequency. May be <code>null</code>.
	 * @param instance
	 *            The optional requested quote instance identifier to be associated.
	 *            The related instance must be in the same provider.
	 * @param optimized
	 *            The optional requested optimized. May be <code>null</code>.
	 * @param location
	 *            The expected location name. Case insensitive.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The cheapest storage or <code>null</code>. The first item corresponds
	 *         to the storage price, the second is the computed price.
	 */
	@Query("SELECT sp, (sp.cost+(CASE WHEN st.minimal < :size THEN st.minimal ELSE :size END) * sp.costGb) AS cost"
			+ " FROM #{#entityName} AS sp LEFT JOIN sp.location loc INNER JOIN sp.type st             "
			+ " WHERE (:node = st.node.id OR :node LIKE CONCAT(st.node.id,'%')) "
			+ " AND (:size IS NULL OR st.maximal IS NULL OR st.maximal >= :size)"
			+ " AND (:instance IS NULL OR (st.instanceCompatible = true"
			+ "       AND EXISTS(SELECT 1 FROM ProvQuoteInstance qi LEFT JOIN qi.location qiloc"
			+ "        WHERE qi.configuration.subscription.node.refined = st.node AND (qiloc IS NULL OR loc IS NULL OR qiloc=loc))))"
			+ " AND (:frequency IS NULL OR st.frequency = :frequency)           "
			+ " AND (loc IS NULL OR UPPER(loc.name) = UPPER(:location))  "
			+ " AND (:optimized IS NULL OR st.optimized = :optimized) ORDER BY cost ASC")
	List<Object[]> findLowestPrice(String node, int size, ProvStorageFrequency frequency, Integer instance, ProvStorageOptimized optimized,
			String location, Pageable pageable);

	/**
	 * Return the {@link ProvStoragePrice} by it's name and the location and related
	 * to given subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param name
	 *            The name to match. Case insensitive.
	 * @param location
	 *            The expected location name. Case insensitive.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT sp FROM ProvStoragePrice sp, Subscription s INNER JOIN s.node AS sn LEFT JOIN sp.location AS loc INNER JOIN sp.type AS st"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(st.node.id, ':%') AND UPPER(st.name) = UPPER(:name) "
			+ " AND (loc IS NULL OR UPPER(loc.name) = UPPER(:location))")
	ProvStoragePrice findByName(int subscription, String name, String location);
}
