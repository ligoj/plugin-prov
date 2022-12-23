/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvSupportPrice} repository.
 */
public interface ProvSupportPriceRepository extends RestRepository<ProvSupportPrice, Integer> {

	/**
	 * Return the cheapest support configuration from the minimal requirements.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return The cheapest support or <code>null</code>.
	 */
	@Query("SELECT sp FROM #{#entityName} AS sp INNER JOIN sp.type st "
			+ " WHERE (:node = st.node.id OR :node LIKE CONCAT(st.node.id,'%')) ")
	List<ProvSupportPrice> findAll(String node);

	/**
	 * Return the {@link ProvSupportPrice} by its name and related to given subscription.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param type         The type name to match. Case-insensitive.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT sp FROM #{#entityName} sp, Subscription s INNER JOIN s.node AS sn INNER JOIN sp.type AS st"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(st.node.id, ':%') AND UPPER(st.name) = UPPER(:type)")
	ProvSupportPrice findByTypeName(int subscription, String type);

}
