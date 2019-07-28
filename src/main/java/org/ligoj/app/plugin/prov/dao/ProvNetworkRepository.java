/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvNetwork;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvNetwork} repository.
 */
public interface ProvNetworkRepository extends RestRepository<ProvNetwork, Integer> {

	/**
	 * Return all {@link ProvNetwork} related to given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier to match.
	 * @return All {@link ProvNetwork} related to given subscription identifier.
	 */
	@Query("FROM #{#entityName} WHERE configuration.subscription.id = :subscription")
	List<ProvNetwork> findAll(int subscription);
}
