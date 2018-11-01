/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvSupportType} repository.
 */
public interface ProvSupportTypeRepository extends RestRepository<ProvSupportType, Integer> {

	/**
	 * Return all {@link ProvSupportType} related to given subscription identifier.
	 *
	 * @param subscription
	 *            The subscription identifier to match.
	 * @param criteria
	 *            The option criteria to match for the name.
	 * @param pageRequest
	 *            The page request for ordering.
	 * @return The filtered {@link ProvSupportType}.
	 */
	@Query("SELECT st FROM #{#entityName} st, Subscription s INNER JOIN s.node AS sn INNER JOIN st.node AS stn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(stn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(st.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvSupportType> findAll(int subscription, String criteria, Pageable pageRequest);

}
