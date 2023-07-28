/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvQuoteContainer} repository.
 */
public interface ProvQuoteContainerRepository
		extends ProvQuoteTermRepository<ProvQuoteContainer>, BasePovInstanceBehavior {

	@Override
	@Modifying
	@Query("DELETE FROM ProvQuoteStorage WHERE quoteContainer IS NOT NULL AND configuration = :quote")
	void deleteAllStorages(ProvQuote quote);

	@Override
	@Query("SELECT id FROM ProvQuoteStorage WHERE quoteContainer IS NOT NULL AND configuration = :quote")
	List<Integer> findAllStorageIdentifiers(ProvQuote quote);

}
