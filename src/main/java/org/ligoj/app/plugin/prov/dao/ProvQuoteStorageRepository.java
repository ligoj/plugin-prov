package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link ProvQuoteStorage} repository.
 */
public interface ProvQuoteStorageRepository extends RestRepository<ProvQuoteStorage, Integer> {

	/**
	 * Delete all storages linked to an instance linked to the given
	 * subscription.
	 * 
	 * @param subscription
	 *            The related subscription identifier.
	 */
	@Modifying
	@Query("DELETE FROM ProvQuoteStorage WHERE id IN"
			+ " (SELECT id FROM ProvQuoteStorage WHERE quoteInstance.configuration.subscription.id = :subscription)")
	void deleteAllAttached(@Param("subscription") int subscription);

}
