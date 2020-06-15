/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuote;

/**
 * Shared storage operations.
 */
public interface BasePovInstanceBehavior {

	/**
	 * Delete all storages linked to a resource linked to the given quote.
	 *
	 * @param quote The related quote.
	 */
	void deleteAllStorages(ProvQuote quote);

	/**
	 * Return identifiers of all storages linked to a resource linked to the given quote.
	 *
	 * @param quote The related quote.
	 * @return Identifiers of all storages linked to a resource linked to the given quote.
	 */
	List<Integer> findAllStorageIdentifiers(ProvQuote quote);

}
