/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractInstanceType} base repository.
 * 
 * @param <T> The instance type type.
 */
@NoRepositoryBean
public interface BaseProvInstanceTypeRepository<T extends AbstractInstanceType> extends RestRepository<T, Integer> {

	/**
	 * Return all {@link ProvInstanceType} related to given subscription identifier.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param criteria     The option criteria to match for the name.
	 * @param pageRequest  The page request for ordering.
	 * @return The filtered {@link ProvInstanceType}.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%')"
			+ " AND (:criteria IS NULL OR UPPER(i.name) LIKE CONCAT(CONCAT('%', UPPER(:criteria)), '%'))")
	Page<T> findAll(int subscription, String criteria, Pageable pageRequest);

	/**
	 * Return the {@link ProvInstanceType} by it's code. Case is sensitive.
	 *
	 * @param subscription The subscription identifier to match.
	 * @param code         The code to match.
	 *
	 * @return The entity or <code>null</code>.
	 */
	@Query("SELECT i FROM #{#entityName} i, Subscription s INNER JOIN s.node AS sn INNER JOIN i.node AS n"
			+ " WHERE s.id = :subscription AND sn.id LIKE CONCAT(n.id, ':%') AND i.code = :code")
	T findByCode(int subscription, String code);

	/**
	 * Return all distinct processors.
	 * 
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return All distinct processors.
	 */
	@Query("SELECT DISTINCT processor FROM #{#entityName} AS t WHERE processor IS NOT NULL "
			+ " AND t.node.id = :node OR t.node.id LIKE CONCAT(:node, ':%')                "
			+ " ORDER BY processor           ")
	List<String> findProcessors(String node);

	/**
	 * Return the valid database types matching the requirements.
	 *
	 * @param node        The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu         The minimum CPU.
	 * @param ram         The minimum RAM in MB.
	 * @param constant    The optional constant CPU behavior constraint.
	 * @param physical    The optional physical (not virtual) instance type constraint.
	 * @param type        The optional instance type identifier. May be <code>null</code>.
	 * @param processor   Optional processor requirement. A <code>LIKE</code> will be used.
	 * @param autoScale   Optional auto-scaling capability requirement.
	 * @param cpuRate     Optional minimal CPU rate.
	 * @param ramRate     Optional minimal RAM rate.
	 * @param networkRate Optional minimal network rate.
	 * @param storageRate Optional minimal storage rate.
	 * @return The matching database instance types.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE                          "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))"
			+ "  AND (:type IS NULL OR id = :type)                       "
			+ "  AND cpu != 0 AND cpu>= :cpu AND ram>=:ram               "
			+ "  AND (:constant IS NULL OR constant = :constant)         "
			+ "  AND (:physical IS NULL OR physical = :physical)         "
			+ "  AND (:autoScale = FALSE OR autoScale = :autoScale)      "
			+ "  AND (:cpuRate IS NULL OR cpuRate >= :cpuRate)           "
			+ "  AND (:ramRate IS NULL OR ramRate >= :ramRate)           "
			+ "  AND (:networkRate IS NULL OR networkRate >= :networkRate)  "
			+ "  AND (:storageRate IS NULL OR storageRate >= :storageRate)  "
			+ "  AND (:processor IS NULL                                 "
			+ "   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))")
	List<Integer> findValidTypes(String node, double cpu, int ram, Boolean constant, Boolean physical, Integer type,
			String processor, boolean autoScale, Rate storageRate, Rate networkRate, Rate ramRate, Rate cpuRate);

	/**
	 * Return the valid instance types matching the requirements.
	 *
	 * @param node        The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu         The minimum CPU.
	 * @param ram         The minimum RAM in MB.
	 * @param constant    The optional constant CPU behavior constraint.
	 * @param physical    The optional physical (not virtual) instance type constraint.
	 * @param type        The optional instance type identifier. May be <code>null</code>.
	 * @param processor   Optional processor requirement. A <code>LIKE</code> will be used.
	 * @param autoScale   Optional auto-scaling capability requirement.
	 * @param cpuRate     Optional minimal CPU rate.
	 * @param ramRate     Optional minimal RAM rate.
	 * @param networkRate Optional minimal network rate.
	 * @param storageRate Optional minimal storage rate.
	 * @return The matching dynamic instance types.
	 */
	@Query("SELECT id FROM #{#entityName} WHERE                          "
			+ "      (:node = node.id OR :node LIKE CONCAT(node.id,':%'))"
			+ "  AND (:type IS NULL OR id = :type)                       "
			+ "  AND cpu = 0                                             "
			+ "  AND (:constant IS NULL OR constant = :constant)         "
			+ "  AND (:physical IS NULL OR physical = :physical)         "
			+ "  AND (:autoScale = FALSE OR autoScale = :autoScale)      "
			+ "  AND (:cpuRate IS NULL OR cpuRate >= :cpuRate)           "
			+ "  AND (:ramRate IS NULL OR ramRate >= :ramRate)           "
			+ "  AND (:networkRate IS NULL OR networkRate >= :networkRate)  "
			+ "  AND (:storageRate IS NULL OR storageRate >= :storageRate)  "
			+ "  AND (:processor IS NULL                                 "
			+ "   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))")
	List<Integer> findDynamicTypes(@CacheKey String node, @CacheKey Boolean constant, @CacheKey Boolean physical,
			@CacheKey Integer type, @CacheKey String processor, boolean autoScale, Rate storageRate, Rate networkRate,
			Rate ramRate, Rate cpuRate);

	/**
	 * Return <code>true</code> when there is at least one dynamic type in this repository.
	 * 
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return <code>true</code> when there is at least one dynamic type in this repository.
	 */
	@Query("SELECT CASE WHEN COUNT(id) > 0 THEN TRUE ELSE FALSE END FROM #{#entityName} WHERE                        "
			+ "  (:node = node.id OR :node LIKE CONCAT(node.id,':%'))    "
			+ "  AND cpu = 0                                             ")
	boolean hasDynamicalTypes(String node);
}
