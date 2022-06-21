/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.stream.Stream;

import org.ligoj.app.plugin.prov.model.ProvOptimizer;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvOptimizer} repository.
 */
public interface ProvOptimizerRepository extends BaseMultiScopedRepository<ProvOptimizer> {

	@Override
	@Query("SELECT pu FROM ProvQuoteInstance pu WHERE pu.optimizer = :scoped"
			+ " OR (pu.optimizer IS NULL AND pu.configuration.optimizer = :scoped)")
	Stream<ProvQuoteInstance> findRelatedInstances(ProvOptimizer scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteDatabase pu WHERE pu.optimizer = :scoped"
			+ " OR (pu.optimizer IS NULL AND  pu.configuration.optimizer = :scoped)")
	Stream<ProvQuoteDatabase> findRelatedDatabases(ProvOptimizer scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteContainer pu WHERE pu.optimizer = :scoped"
			+ " OR (pu.optimizer IS NULL AND  pu.configuration.optimizer = :scoped)")
	Stream<ProvQuoteContainer> findRelatedContainers(ProvOptimizer scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteFunction pu WHERE pu.optimizer = :scoped"
			+ " OR (pu.optimizer IS NULL AND  pu.configuration.optimizer = :scoped)")
	Stream<ProvQuoteFunction> findRelatedFunctions(ProvOptimizer scoped);
}
