/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteSnapshot;
import org.ligoj.bootstrap.core.dao.RestRepository;

/**
 * {@link ProvQuoteSnapshot} repository.
 */
public interface ProvQuoteSnapshotRepository extends RestRepository<ProvQuoteSnapshot, Integer> {

	/**
	 * Return the snapshots of a subscription, newest first.
	 *
	 * @param subscription The subscription identifier.
	 * @return The snapshots, newest first.
	 */
	List<ProvQuoteSnapshot> findAllBySubscriptionIdOrderByIdDesc(int subscription);
}
