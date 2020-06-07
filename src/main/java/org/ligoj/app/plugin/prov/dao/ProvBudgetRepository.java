/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvBudget} repository.
 */
public interface ProvBudgetRepository extends RestRepository<ProvBudget, Integer> {

	/**
	 * Return all {@link ProvBudget} related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The optional criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvBudget}.
	 */
	@Query("SELECT pu FROM ProvBudget pu WHERE pu.configuration.subscription.id = :subscription"
			+ " AND UPPER(pu.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%') ORDER BY UPPER(pu.name)")
	Page<ProvBudget> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return all {@link ProvBudget} related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @return The {@link ProvBudget} ordered by its name.
	 */
	@Query("SELECT pu FROM ProvBudget pu WHERE pu.configuration.subscription.id = :subscription ORDER BY UPPER(pu.name)")
	List<ProvBudget> findAll(int subscription);

	/**
	 * Return the {@link ProvBudget} by it's name, ignoring the case.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param name         The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pu FROM ProvBudget pu WHERE pu.configuration.subscription.id = :subscription AND UPPER(pu.name) = UPPER(:name)")
	ProvBudget findByName(int subscription, String name);
}
