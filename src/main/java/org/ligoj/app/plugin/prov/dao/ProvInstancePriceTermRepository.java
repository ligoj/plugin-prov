/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvInstancePriceTerm} repository.
 */
public interface ProvInstancePriceTermRepository extends RestRepository<ProvInstancePriceTerm, Integer> {

	/**
	 * Return all {@link ProvInstancePriceTerm} related to given subscription identifier.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The optional criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvInstancePriceTerm}.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceTerm ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(ipt.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<ProvInstancePriceTerm> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstancePriceTerm} by it's name, ignoring the case.
	 * 
	 * @param subscription The subscription identifier to match.
	 * @param name         The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT ipt FROM ProvInstancePriceTerm ipt, Subscription s INNER JOIN s.node AS sn INNER JOIN ipt.node AS iptn"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(iptn.id, ':%') AND UPPER(ipt.name) = UPPER(:name)")
	ProvInstancePriceTerm findByName(int subscription, String name);

	/**
	 * Return the {@link ProvInstancePriceTerm} by it's name for a specific provider, ignoring the case.
	 * 
	 * @param node The provider node identifier.
	 * @param name The name to match.
	 * 
	 * @return The entity or <code>null</code>.
	 */
	@Query("FROM ProvInstancePriceTerm WHERE node.id=:node AND UPPER(name) = UPPER(:name)")
	ProvInstancePriceTerm findByName(String node, String name);

	/**
	 * Return the valid terms matching the requirements.
	 *
	 * @param node         The node linked to the subscription. Is a node identifier within a provider.
	 * @param convOs       When <code>true</code>, terms convertible OS are required.
	 * @param convEngine   When <code>true</code>, terms convertible engine are required.
	 * @param convType     When <code>true</code>, terms convertible type are required.
	 * @param convFamily   When <code>true</code>, terms convertible family are required.
	 * @param convLocation When <code>true</code>, terms convertible location are required.
	 * @param reservation  When <code>true</code>, terms with reservation are required.
	 * @param maxPeriod    Maximal accepted period.
	 * @param ephemeral    When <code>true</code>, ephemeral contract is accepted. Otherwise (<code>false</code>), only
	 *                     non ephemeral instance are accepted.
	 * @param location     The required location.
	 * @return The matching instance terms.
	 */
	@CacheResult(cacheName = "prov-instance-term")
	@Query("SELECT id FROM #{#entityName} WHERE                                         "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))               "
			+ "  AND (:convOs = FALSE OR :convOs = convertibleOs)                       "
			+ "  AND (:convEngine = FALSE OR :convEngine = convertibleEngine)           "
			+ "  AND (:convType = FALSE OR :convType = convertibleType)                 "
			+ "  AND (:convFamily = FALSE OR :convFamily = convertibleFamily)           "
			+ "  AND (:convLocation = FALSE OR :convLocation = convertibleLocation)     "
			+ "  AND (:reservation = FALSE OR :reservation = reservation)               "
			+ "  AND (:ephemeral = TRUE OR ephemeral = FALSE)                           "
			+ "  AND (location IS NULL OR location.id = :location)                      "
			+ "  AND :maxPeriod >= period     ")
	List<Integer> findValidTerms(@CacheKey String node, @CacheKey boolean convOs, @CacheKey boolean convEngine,
			@CacheKey boolean convType, @CacheKey boolean convFamily, @CacheKey boolean convLocation,
			@CacheKey boolean reservation, @CacheKey double maxPeriod, @CacheKey boolean ephemeral,
			@CacheKey int location);
}
