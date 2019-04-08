/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.CurrencyVo;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvCurrency} repository.
 */
public interface ProvCurrencyRepository extends RestRepository<ProvCurrency, Integer> {

	@Query(value = "SELECT new org.ligoj.app.plugin.prov.model.CurrencyVo(c, COUNT(q))"
			+ " FROM ProvCurrency c LEFT JOIN ProvQuote q ON q.currency = c"
			+ " WHERE UPPER(c.name) LIKE CONCAT('%',CONCAT(UPPER(:search),'%'))"
			+ "       OR UPPER(c.description) LIKE CONCAT('%',CONCAT(UPPER(:search),'%'))       "
			+ "       GROUP BY c.id", countQuery = "SELECT COUNT(c) FROM ProvCurrency c"
					+ " WHERE UPPER(c.name) LIKE CONCAT('%',CONCAT(UPPER(:search),'%'))"
					+ "       OR UPPER(c.description) LIKE CONCAT('%',CONCAT(UPPER(:search),'%'))")
	Page<CurrencyVo> findAll(String search, Pageable pageRequest);

}
