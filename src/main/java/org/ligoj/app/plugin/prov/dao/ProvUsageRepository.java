/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvUsage} repository.
 */
public interface ProvUsageRepository extends RestRepository<ProvUsage, Integer> {

	/**
	 * Return all {@link ProvUsage} related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The optional criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvUsage}.
	 */
	@Query("SELECT pu FROM ProvUsage pu WHERE pu.configuration.subscription.id = :subscription"
			+ " AND UPPER(pu.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%') ORDER BY UPPER(pu.name)")
	Page<ProvUsage> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return all {@link ProvUsage} related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @return The {@link ProvUsage} ordered by its name.
	 */
	@Query("SELECT pu FROM ProvUsage pu WHERE pu.configuration.subscription.id = :subscription ORDER BY UPPER(pu.name)")
	List<ProvUsage> findAll(int subscription);

	/**
	 * Return the {@link ProvUsage} by its identifier and also valid for the given subscription.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param id           The entity's identifier to match.
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pu FROM ProvUsage pu WHERE pu.configuration.subscription.id = :subscription AND pu.id = :id")
	ProvUsage findById(int subscription, Integer id);

	/**
	 * Return the {@link ProvUsage} by it's name, ignoring the case.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param name         The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pu FROM ProvUsage pu WHERE pu.configuration.subscription.id = :subscription AND UPPER(pu.name) = UPPER(:name)")
	ProvUsage findByName(int subscription, String name);
}
