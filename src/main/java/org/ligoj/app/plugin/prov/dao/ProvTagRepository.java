/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvTag;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvTag} repository.
 */
public interface ProvTagRepository extends RestRepository<ProvTag, Integer> {

	/**
	 * Return all {@link ProvTag} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @return All {@link ProvTag} related to given subscription identifier.
	 */
	@Query("FROM #{#entityName} WHERE configuration.subscription.id = :subscription")
	List<ProvTag> findAll(int subscription);
}
