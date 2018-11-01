/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

/**
 * {@link AbstractQuoteResource} repository.
 */
@NoRepositoryBean
public interface ProvQuoteResourceRepository<R extends AbstractQuoteResource<?>> extends RestRepository<R, Integer> {

	/**
	 * Return identifiers of all resources linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE configuration.subscription.id = :subscription")
	List<Integer> findAllIdentifiers(@Param("subscription") int subscription);

}
