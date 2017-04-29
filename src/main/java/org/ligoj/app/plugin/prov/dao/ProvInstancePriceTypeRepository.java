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
	 *            The option criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvInstancePriceType}.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceType ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%')"
			+ " AND :criteria IS NULL OR UPPER(ipt.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%')")
	Page<ProvInstancePriceType> findAll(int subscription, String criteria, Pageable pageRequest);
}
