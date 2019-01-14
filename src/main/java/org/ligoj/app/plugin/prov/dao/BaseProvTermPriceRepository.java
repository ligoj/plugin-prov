/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link ProvDatabasePrice} repository.
 */
@NoRepositoryBean
public interface BaseProvTermPriceRepository<T extends AbstractInstanceType, P extends AbstractTermPrice<T>>
		extends RestRepository<P, Integer> {

	/**
	 * Return all {@link ProvInstancePrice} related to given node and within a specific location.
	 *
	 * @param node
	 *            The node (provider) to match.
	 * @param location
	 *            The expected location name. Case sensitive.
	 * @return The filtered {@link ProvInstancePrice}.
	 */
	@Query("FROM #{#entityName} WHERE location.name = :location AND type.node.id = :node")
	List<P> findAll(String node, String location);

}
