/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractQuote} repository.
 * 
 * @param <C> Quote resource type.
 */
@NoRepositoryBean
public interface BaseProvQuoteRepository<C extends AbstractQuote<?>> extends RestRepository<C, Integer> {

	/**
	 * Return identifiers of all resources linked to the given quote.
	 *
	 * @param quote The related quote identifier.
	 * @return Return identifiers of all resources linked to the given quote.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE configuration = :quote")
	List<Integer> findAllIdentifiers(ProvQuote quote);

	/**
	 * Return the quote instances from the related quote.
	 *
	 * @param quote The filtered quote.
	 * @return The instance quote details with the optional linked instance.
	 */
	@Query("FROM #{#entityName} AS qi INNER JOIN FETCH qi.price qsp INNER JOIN FETCH qsp.type"
			+ " WHERE qi.configuration = :quote")
	List<C> findAll(ProvQuote quote);

	/**
	 * Return the resource identifier from the related quote, and only if this resource can be associated to network.
	 *
	 * @param quote The filtered quote.
	 * @return The resource identifiers.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE configuration = :quote")
	Set<Integer> findAllNetworkId(ProvQuote quote);

	/**
	 * Return the quote item identifier from the related quote with the related name, and only if this resource can be
	 * associated to network.
	 *
	 * @param quote The filtered quote.
	 * @return The resource identifiers with its name.
	 */
	@Query("SELECT id, name FROM #{#entityName} WHERE configuration = :quote")
	List<Object[]> findAllNetworkIdName(ProvQuote quote);

	/**
	 * Return used price codes among the quotes relate to a given node.
	 *
	 * @param node The related node identifier.
	 * @return Used price codes among the quotes relate to a given node.
	 */
	@Query("SELECT DISTINCT p.code FROM #{#entityName} c INNER JOIN c.price p WHERE p.type.node.id = :node")
	Collection<String> finUsedPrices(String node);

}
