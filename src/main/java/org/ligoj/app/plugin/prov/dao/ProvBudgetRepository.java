/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.stream.Stream;

import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvBudget} repository.
 */
public interface ProvBudgetRepository extends BaseMultiScopedRepository<ProvBudget> {

	@Override
	@Query("SELECT pu FROM ProvQuoteInstance pu WHERE pu.budget = :scoped"
			+ " OR (pu.budget IS NULL AND pu.configuration.budget = :scoped)")
	Stream<ProvQuoteInstance> findRelatedInstances(ProvBudget scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteDatabase pu WHERE pu.budget = :scoped"
			+ " OR (pu.budget IS NULL AND pu.configuration.budget = :scoped)")
	Stream<ProvQuoteDatabase> findRelatedDatabases(ProvBudget scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteContainer pu WHERE pu.budget = :scoped"
			+ " OR (pu.budget IS NULL AND pu.configuration.budget = :scoped)")
	Stream<ProvQuoteContainer> findRelatedContainers(ProvBudget scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteFunction pu WHERE pu.budget = :scoped"
			+ " OR (pu.budget IS NULL AND pu.configuration.budget = :scoped)")
	Stream<ProvQuoteFunction> findRelatedFunctions(ProvBudget scoped);
}
