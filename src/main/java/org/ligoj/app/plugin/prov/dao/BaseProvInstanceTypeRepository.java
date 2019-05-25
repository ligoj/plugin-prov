/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractInstanceType} base repository.
 * 
 * @param <T> The instance type type.
 */
@NoRepositoryBean
public interface BaseProvInstanceTypeRepository<T extends AbstractInstanceType> extends RestRepository<T, Integer> {

	/**
	 * Return all {@link ProvInstanceType} related to given subscription identifier.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The option criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvInstanceType}.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(i.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<T> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstanceType} by it's name, ignoring the case.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param name         The name to match.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%') AND UPPER(i.name) = UPPER(:name)")
	T findByName(int subscription, String name);
}
