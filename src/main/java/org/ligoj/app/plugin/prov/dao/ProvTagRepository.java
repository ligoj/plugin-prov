/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvTag;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvTag} repository.
 */
@SuppressWarnings("unused")
public interface ProvTagRepository extends RestRepository<ProvTag, Integer> {

	/**
	 * Return all {@link ProvTag} related to given quote identifier.
	 *
	 * @param quote The quote identifier to match.
	 * @return All {@link ProvTag} related to given quote identifier.
	 */
	@Query("FROM ProvTag WHERE configuration = :quote")
	List<ProvTag> findAll(ProvQuote quote);
}
