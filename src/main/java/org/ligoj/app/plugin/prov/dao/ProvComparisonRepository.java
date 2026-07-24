/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvComparison;
import org.ligoj.bootstrap.core.dao.RestRepository;

/**
 * {@link ProvComparison} repository.
 */
public interface ProvComparisonRepository extends RestRepository<ProvComparison, Integer> {

	/**
	 * Return the comparisons (CS links) of a main subscription (MS).
	 *
	 * @param mainSubscription The main subscription identifier.
	 * @return The comparisons.
	 */
	List<ProvComparison> findAllByMainSubscriptionId(int mainSubscription);

	/**
	 * Return the comparison linking the given MS and CS, or <code>null</code>.
	 *
	 * @param mainSubscription The main subscription identifier.
	 * @param subscription     The compared subscription identifier.
	 * @return The comparison or <code>null</code>.
	 */
	ProvComparison findByMainSubscriptionIdAndSubscriptionId(int mainSubscription, int subscription);
}
