/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;
import java.util.stream.Stream;

import org.ligoj.app.plugin.prov.model.AbstractMultiScoped;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * Multi scoped resource repository.
 */
@NoRepositoryBean
public interface BaseMultiScopedRepository<S extends AbstractMultiScoped> extends RestRepository<S, Integer> {

	/**
	 * Return all resources related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The optional criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered resources.
	 */
	@Query("SELECT pu FROM #{#entityName} pu WHERE pu.configuration.subscription.id = :subscription"
			+ " AND UPPER(pu.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%') ORDER BY UPPER(pu.name)")
	Page<S> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return all resources related to given quote identifier.
	 * 
	 * @param quote The quote identifier to match.
	 * @return The resources ordered by its name.
	 */
	@Query("SELECT pu FROM #{#entityName} pu WHERE pu.configuration = :quote ORDER BY UPPER(pu.name)")
	List<S> findAll(ProvQuote quote);

	/**
	 * Return all instances related to given entity.
	 * 
	 * @param scoped The related entity to match.
	 * @return The resources.
	 */
	Stream<ProvQuoteInstance> findRelatedInstances(S scoped);

	/**
	 * Return all databases related to given entity.
	 * 
	 * @param scoped The related entity to match.
	 * @return The resources.
	 */
	Stream<ProvQuoteDatabase> findRelatedDatabases(S scoped);

	/**
	 * Return the resource by it's name, ignoring the case.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param name         The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT pu FROM #{#entityName} pu WHERE pu.configuration.subscription.id = :subscription AND UPPER(pu.name) = UPPER(:name)")
	S findByName(int subscription, String name);
}
