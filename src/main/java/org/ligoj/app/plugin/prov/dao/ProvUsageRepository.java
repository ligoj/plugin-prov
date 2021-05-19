/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.stream.Stream;

import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvUsage} repository.
 */
public interface ProvUsageRepository extends BaseMultiScopedRepository<ProvUsage> {

	@Override
	@Query("SELECT pu FROM ProvQuoteInstance pu WHERE pu.usage = :scoped"
			+ " OR (pu.usage IS NULL AND pu.configuration.usage = :scoped)")
	Stream<ProvQuoteInstance> findRelatedInstances(ProvUsage scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteDatabase pu WHERE pu.usage = :scoped"
			+ " OR (pu.usage IS NULL AND  pu.configuration.usage = :scoped)")
	Stream<ProvQuoteDatabase> findRelatedDatabases(ProvUsage scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteContainer pu WHERE pu.usage = :scoped"
			+ " OR (pu.usage IS NULL AND  pu.configuration.usage = :scoped)")
	Stream<ProvQuoteContainer> findRelatedContainers(ProvUsage scoped);

	@Override
	@Query("SELECT pu FROM ProvQuoteFunction pu WHERE pu.usage = :scoped"
			+ " OR (pu.usage IS NULL AND  pu.configuration.usage = :scoped)")
	Stream<ProvQuoteFunction> findRelatedFunctions(ProvUsage scoped);
}
