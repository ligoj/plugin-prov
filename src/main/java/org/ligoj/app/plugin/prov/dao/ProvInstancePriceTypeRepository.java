package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePriceType} repository.
 */
public interface ProvInstancePriceTypeRepository extends RestRepository<ProvInstancePriceType, Integer> {

	/**
	 * Return all {@link ProvInstancePriceType} related to given subscription
	 * identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The optional criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvInstancePriceType}.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceType ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(ipt.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvInstancePriceType> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstancePriceType} by its identifier and also valid
	 * for the given subscription.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param id
	 *            The entity's identifier to match.
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceType ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%') AND ipt.id = :id")
	ProvInstancePriceType findById(int subscription, Integer id);

	/**
	 * Return the {@link ProvInstancePriceType} by  it's name, ignoring the case.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param name
	 *            The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceType ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%') AND UPPER(ipt.name) = UPPER(:name)")
	ProvInstancePriceType findByName(int subscription, String name);
}
