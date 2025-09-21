/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractTermPriceVm;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractTermPriceVm} repository.
 *
 * @param <T> The instance type's type.
 * @param <P> The price type.
 */
@NoRepositoryBean
public interface BaseProvTermPriceVmRepository<T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>>
		extends BaseProvTermPriceRepository<T, P> {

	String DYNAMIC_QUERY_VM = """
			SELECT ip,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.costGpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * (CASE WHEN ip.period = 0 THEN CAST(:globalRate AS Double) ELSE (ip.period * CEIL(:duration/ip.period)) END) AS totalCost,
			 (  ip.cost
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.costCpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.costGpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.costRam
			 )
			 * CASE WHEN ip.period = 0 THEN CAST(:rate AS Double) ELSE 1.0 END AS monthlyCost,

			 (  ip.co2
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.co2Cpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.co2Gpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.co2Ram
			 )
			 * CASE WHEN ip.period = 0 THEN CAST(:globalRate AS Double) ELSE (ip.period * CEIL(:duration/ip.period)) END AS totalCo2,
			 (  ip.co2
			  + CEIL(GREATEST(ip.minCpu, :cpu) /ip.incrementCpu) * ip.incrementCpu * ip.co2Cpu
			  + CASE WHEN (ip.incrementGpu IS NULL OR ip.incrementGpu=0.0) THEN 0.0 ELSE (CEIL(GREATEST(ip.minGpu, :gpu) /ip.incrementGpu) * ip.incrementGpu * ip.co2Gpu) END
			  + CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio,0.0), :ram) /ip.incrementRam) * ip.incrementRam * ip.co2Ram
			 )
			 * CASE WHEN ip.period = 0 THEN CAST(:rate AS Double) ELSE 1.0 END AS monthlyCo2
			 FROM #{#entityName} ip WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NOT NULL
			  AND (ip.license IS NULL OR :license = ip.license)
			  AND (ip.type.id IN :types)
			  AND (ip.term.id IN :terms)
			  AND (ip.p1Type IS NULL OR :p1TypeOnly = FALSE)
			  AND (ip.maxCpu  IS NULL OR ip.maxCpu >=:cpu)
			  AND (ip.maxGpu  IS NULL OR ip.maxGpu >=:gpu)
			  AND (ip.maxRam  IS NULL OR ip.maxRam >=:ram)
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.maxRamRatio IS NULL OR GREATEST(ip.minCpu, :cpu) * ip.maxRamRatio <= :ram)
			""";

	String LOWEST_QUERY_VM = LOWEST_QUERY_TERM + """
			  AND (ip.license IS NULL OR :license = ip.license)
			""";

	@Override
	@Query("SELECT COUNT(id) FROM #{#entityName} WHERE type.node.id = :node AND (co2 > 0 OR co2Cpu > 0)")
	int countCo2DataByNode(String node);
}
