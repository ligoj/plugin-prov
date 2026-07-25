/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteView;
import org.ligoj.bootstrap.core.dao.RestRepository;

/**
 * {@link ProvQuoteView} repository.
 */
public interface ProvQuoteViewRepository extends RestRepository<ProvQuoteView, Integer> {

	/**
	 * Return the shared views of a subscription, sorted by name.
	 *
	 * @param subscription The subscription identifier.
	 * @return The shared views.
	 */
	List<ProvQuoteView> findAllBySubscriptionIdOrderByNameAsc(int subscription);

	/**
	 * Return the view carrying the given name on the subscription, or <code>null</code>.
	 *
	 * @param subscription The subscription identifier.
	 * @param name         The view name.
	 * @return The matching view or <code>null</code>.
	 */
	ProvQuoteView findBySubscriptionIdAndName(int subscription, String name);
}
