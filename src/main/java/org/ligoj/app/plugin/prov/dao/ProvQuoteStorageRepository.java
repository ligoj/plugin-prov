/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;
import java.util.Set;

import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuoteStorage} repository.
 */
public interface ProvQuoteStorageRepository extends BaseProvQuoteRepository<ProvQuoteStorage> {

	@Override
	@Query("SELECT id FROM ProvQuoteStorage WHERE configuration = :quote AND price.type.network IS NOT NULL")
	Set<Integer> findAllNetworkId(ProvQuote quote);

	@Override
	@Query("SELECT id, name FROM ProvQuoteStorage WHERE configuration = :quote AND price.type.network IS NOT NULL")
	List<Object[]> findAllNetworkIdName(ProvQuote quote);

}
