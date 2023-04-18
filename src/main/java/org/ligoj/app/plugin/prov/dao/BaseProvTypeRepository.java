/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.AbstractCodedEntity;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvType;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link ProvType} base repository.
 *
 * @param <T> The instance type's type.
 */
@NoRepositoryBean
public interface BaseProvTypeRepository<T extends AbstractCodedEntity> extends RestRepository<T, Integer> {

	/**
	 * Return all {@link ProvType} related to given subscription identifier.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The option criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvInstanceType}.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%')"
			+ " AND (:criteria = '' OR UPPER(i.name) LIKE CONCAT(CONCAT('%', :criteria), '%'))")
	Page<T> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvType} by its code. Case is sensitive.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param code         The code to match.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%') AND i.code = :code")
	T findByCode(int subscription, String code);
}
