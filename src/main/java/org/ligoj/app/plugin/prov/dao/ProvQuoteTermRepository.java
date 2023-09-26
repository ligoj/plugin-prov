/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.AbstractTermPriceVm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * {@link AbstractQuote} having a term repository.
 *
 * @param <C> Quote resource type.
 */
@SuppressWarnings("unused")
@NoRepositoryBean
public interface ProvQuoteTermRepository<C extends AbstractQuote<? extends AbstractTermPriceVm<?>>>
		extends BaseProvQuoteRepository<C>, BasePovInstanceBehavior {

	/**
	 * Return the quote instances from the related quote.
	 *
	 * @param quote The filtered quote.
	 * @return The instance quote details with the optional linked instance.
	 */
	@Override
	@Query("""
			FROM #{#entityName} AS qi
			INNER JOIN FETCH qi.price qsp
			INNER JOIN FETCH qsp.type
			LEFT JOIN FETCH qsp.location
			INNER JOIN FETCH qsp.term
			INNER JOIN FETCH qi.configuration c
			LEFT JOIN FETCH c.location l
			WHERE qi.configuration = :quote
			""")
	List<C> findAll(ProvQuote quote);

}
