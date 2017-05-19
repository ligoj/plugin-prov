package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvStorageType} repository.
 */
public interface ProvStorageTypeRepository extends RestRepository<ProvStorageType, Integer> {

	/**
	 * Return all {@link ProvStorageType} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The option criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvStorageType}.
	 */
	@Query("SELECT st FROM #{#entityName} st, Subscription s INNER JOIN s.node AS sn INNER JOIN st.node AS stn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(stn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(st.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvStorageType> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the lowest storage price configuration from the minimal requirements.
	 * 
	 * @param node
	 *            The node linked to the subscription. Is a node identifier within a provider.
	 * @param size
	 *            The requested size in GB.
	 * @param frequency
	 *            The optional requested frequency. May be <code>null</code>.
	 * @param instance
	 *            The optional requested instance to be associated.
	 * @param optimized
	 *            The optional requested optimized. May be <code>null</code>.
	 * @param pageable
	 *            The page control to return few item.
	 * @return The minimum instance or <code>null</code>.
	 */
	@Query("FROM #{#entityName} AS st WHERE (:node = st.node.id OR :node LIKE CONCAT(st.node.id,'%'))"
			+ " AND (:size IS NULL OR st.maximal IS NULL OR st.maximal >= :size)"
			+ " AND (:instance IS NULL OR st.instanceCompatible = true)"
			+ " AND (:frequency IS NULL OR st.frequency = :frequency)"
			+ " AND (:optimized IS NULL OR st.optimized = :optimized) ORDER BY st.cost ASC")
	List<ProvStorageType> findLowestPrice(String node, int size, ProvStorageFrequency frequency, Integer instance,
			ProvStorageOptimized optimized, Pageable pageRequest);
}
