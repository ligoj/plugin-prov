/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractInstanceType} base repository.
 *
 * @param <T> The instance type type.
 */
@SuppressWarnings("ALL")
@NoRepositoryBean
public interface BaseProvInstanceTypeRepository<T extends AbstractInstanceType>
		extends BaseProvTypeRepository<T>, Co2Type {

	/**
	 * Return all distinct processors.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return All distinct processors.
	 */
	@Query("SELECT DISTINCT processor FROM #{#entityName} AS t WHERE processor IS NOT NULL "
			+ " AND t.node.id = :node ORDER BY processor           ")
	List<String> findProcessors(String node);

	String BASE_CRITERIA = """
			SELECT id FROM #{#entityName} WHERE
			       :node = node.id
			  AND (:type = 0 OR id = :type)
			  AND (baseline=0.0 OR :baseline <= baseline)
			  AND (:physical = FALSE OR physical = :physical)
			  AND (:autoScale = FALSE OR autoScale = :autoScale)
			  AND (:edge = FALSE OR edge = :edge)
			  AND (cpuRate IS NULL OR cpuRate >= :cpuRate)
			  AND (ramRate IS NULL OR ramRate >= :ramRate)
			  AND (networkRate IS NULL OR networkRate >= :networkRate)
			  AND (storageRate IS NULL OR storageRate >= :storageRate)
			  AND (:co2Mode = FALSE OR watt > 0)
			  AND (:processor = ''
			   OR (processor IS NOT NULL AND UPPER(processor) LIKE CONCAT('%', CONCAT(UPPER(:processor), '%'))))
			""";

	/**
	 * Return the valid instance types matching the requirements.
	 *
	 * @param node        The node linked to the subscription. Is a node identifier within a provider.
	 * @param cpu         The minimum CPU.
	 * @param gpu         The minimum GPU.
	 * @param ram         The minimum RAM in MB.
	 * @param limitCpu    The maximum CPU. Used only to reduce initial lookup potential result.
	 * @param limitGpu    The maximum GPU. Used only to reduce initial lookup potential result.
	 * @param limitRam    The maximum RAM in MB. Used only to reduce initial lookup potential result.
	 * @param baseline    The baseline CPU usage from 0 to 100.
	 * @param physical    The optional physical (not virtual) instance type constraint.
	 * @param type        The optional instance type identifier. May be <code>null</code>.
	 * @param processor   Optional processor requirement. A <code>LIKE</code> will be used.
	 * @param autoScale   Optional auto-scaling capability requirement.
	 * @param cpuRate     Optional minimal CPU rate.
	 * @param gpuRate     Optional minimal GPU rate.
	 * @param ramRate     Optional minimal RAM rate.
	 * @param networkRate Optional minimal network rate.
	 * @param storageRate Optional minimal storage rate.
	 * @param edge        Optional edge location constraint.
	 * @param co2Mode     When <code>true</code> only types having CO2 data are returned.
	 * @return The matching instance types.
	 */
	@Query(BASE_CRITERIA + """
			  AND (cpu BETWEEN :cpu AND :limitCpu)
			  AND (ram BETWEEN :ram AND :limitRam)
			  AND (:gpu=0.0 OR (gpu IS NOT NULL AND (gpu BETWEEN :gpu AND :limitGpu) AND gpuRate >= :gpuRate))
			""")
	List<Integer> findValidTypes(String node, double cpu, double gpu, double ram, double limitCpu, double limitRam,
			double limitGpu, double baseline, boolean physical, int type, String processor, boolean autoScale,
			Rate cpuRate, Rate gpuRate, Rate ramRate, Rate networkRate, Rate storageRate, boolean edge,
			boolean co2Mode);

	/**
	 * Return the valid instance types matching the requirements.
	 *
	 * @param node        The node linked to the subscription. Is a node identifier within a provider.
	 * @param baseline    The minial baseline CPU percentage, from 1 to 100.
	 * @param physical    The optional physical (not virtual) instance type constraint.
	 * @param type        The optional instance type identifier. May be <code>null</code>.
	 * @param processor   Optional processor requirement. A <code>LIKE</code> will be used.
	 * @param autoScale   Optional auto-scaling capability requirement.
	 * @param cpuRate     Optional minimal CPU rate.
	 * @param gpuRate     Optional minimal GPU rate.
	 * @param ramRate     Optional minimal RAM rate.
	 * @param networkRate Optional minimal network rate.
	 * @param storageRate Optional minimal storage rate.
	 * @param edge        Optional edge location constraint.
	 * @param co2Mode     When <code>true</code> only types having CO2 data are returned.
	 * @return The matching dynamic instance types.
	 */
	@Query(BASE_CRITERIA + """
			  AND cpu = 0
			  AND (gpuRate IS NULL OR gpuRate >= :gpuRate)
			""")
	List<Integer> findDynamicTypes(@CacheKey String node, @CacheKey double baseline, @CacheKey boolean physical,
			@CacheKey int type, @CacheKey String processor, @CacheKey boolean autoScale, @CacheKey Rate cpuRate,
			@CacheKey Rate gpuRate, @CacheKey Rate ramRate, @CacheKey Rate networkRate, @CacheKey Rate storageRate,
			boolean edge, boolean co2Mode);

	/**
	 * Return <code>true</code> when there is at least one dynamic type in this repository.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return <code>true</code> when there is at least one dynamic type in this repository.
	 */
	@Query("""
			SELECT CASE WHEN COUNT(id) > 0 THEN TRUE ELSE FALSE END FROM #{#entityName} WHERE
			  :node = node.id
			  AND cpu = 0""")
	boolean hasDynamicalTypes(String node);

	@Override
	@Query("""
			SELECT CASE WHEN COUNT(id) > 0 THEN TRUE ELSE FALSE END FROM #{#entityName} WHERE
			  :node = node.id
			  AND watt > 0""")
	boolean hasCo2Data(String node);
}
