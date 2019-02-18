/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

/**
 * Shared storage operations.
 */
public interface BasePovInstanceBehavior {

	/**
	 * Delete all storages linked to a resource linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 */
	void deleteAllStorages(int subscription);

	/**
	 * Return identifiers of all storages linked to a resource linked to the given subscription.
	 *
	 * @param subscription
	 *            The related subscription identifier.
	 * @return Identifiers of all storages linked to a resource linked to the given subscription.
	 */
	List<Integer> findAllStorageIdentifiers(int subscription);

}
