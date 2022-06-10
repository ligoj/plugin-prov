/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link ProvFunctionPrice} repository.
 */
public interface ProvFunctionPriceRepository extends BaseProvTermPriceRepository<ProvFunctionType, ProvFunctionPrice> {

	String DYNAMIC_QUERY = """
			SELECT  ip,
			    (  CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration / ip.period)) END
			     * (  ip.cost
			        + (  CEIL(GREATEST(ip.minCpu, :cpu) / ip.incrementCpu)
			           * ip.incrementCpu
			           * ip.costCpu
			           * :reservedConcurrency
			          )
			       )
			    )
			  + (  CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio, 0.0), :ram) / ip.incrementRam)
			     * ip.incrementRam
			     * (
			         (  ip.costRam
			          * CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration / ip.period)) END
			          * :reservedConcurrency
			         )
			       + (  CASE WHEN ip.period = 0 THEN :duration ELSE (ip.period * CEIL(:duration / ip.period)) END
			          * (
			              (  LEAST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                    * ip.incrementDuration
			                    * :nbRequests
			                    / :concurrencyMonth,
			                      :realConcurrency
			                    * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			                 )
			            * ip.costRamRequestConcurrency
			           )
			         + (  GREATEST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                    * ip.incrementDuration
			                    * :nbRequests
			                    / :concurrencyMonth
			                  -   :realConcurrency
			                    * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END,
			                    0.0
			                 )
			            * ip.costRamRequest
			           )
			         )
			      )
			    )
			    )
			  + (  :nbRequests
			     * ip.costRequests
			  	  * CASE WHEN ip.period = 0 THEN :duration ELSE (ip.period * CEIL(:duration / ip.period)) END
			 ) AS totalCost,

			    (  CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			     * (  ip.cost
			        + (  CEIL(GREATEST(ip.minCpu, :cpu) / ip.incrementCpu)
			           * ip.incrementCpu
			           * ip.costCpu
			           * :reservedConcurrency
			          )
			       )
			    )
			  + (  CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio, 0.0), :ram) / ip.incrementRam)
			     * ip.incrementRam
			     * (
			         (  ip.costRam
			          * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			          * :reservedConcurrency
			         )
			       + (
			           (  LEAST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                 * ip.incrementDuration
			                 * :nbRequests
			                 / :concurrencyMonth,
			                   :realConcurrency
			                 * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			              )
			         * ip.costRamRequestConcurrency
			        )
			      + (  GREATEST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                 * ip.incrementDuration
			                 * :nbRequests
			                 / :concurrencyMonth
			               -   :realConcurrency
			                 * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END,
			                 0.0
			               )
			         * ip.costRamRequest
			        )
			      )
			    )
			    )
			  + ( :nbRequests * ip.costRequests ) AS monthlyCost,

			    (  CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration / ip.period)) END
			     * (  ip.co2
			        + (  CEIL(GREATEST(ip.minCpu, :cpu) / ip.incrementCpu)
			           * ip.incrementCpu
			           * ip.co2Cpu
			           * :reservedConcurrency
			          )
			       )
			    )
			  + (  CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio, 0.0), :ram) / ip.incrementRam)
			     * ip.incrementRam
			     * (
			         (  ip.co2Ram
			          * CASE WHEN ip.period = 0 THEN :globalRate ELSE (ip.period * CEIL(:duration / ip.period)) END
			          * :reservedConcurrency
			         )
			       + (  CASE WHEN ip.period = 0 THEN :duration ELSE (ip.period * CEIL(:duration / ip.period)) END
			          * (
			              (  LEAST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                    * ip.incrementDuration
			                    * :nbRequests
			                    / :concurrencyMonth,
			                      :realConcurrency
			                    * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			                 )
			            * ip.co2RamRequestConcurrency
			           )
			         + (  GREATEST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                    * ip.incrementDuration
			                    * :nbRequests
			                    / :concurrencyMonth
			                  -   :realConcurrency
			                    * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END,
			                    0.0
			                 )
			            * ip.co2RamRequest
			           )
			         )
			      )
			    )
			    )
			  + (  :nbRequests
			     * ip.co2Requests
			  	  * CASE WHEN ip.period = 0 THEN :duration ELSE (ip.period * CEIL(:duration / ip.period)) END
			 ) AS totalCo2,

			    (  CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			     * (  ip.co2
			        + (  CEIL(GREATEST(ip.minCpu, :cpu) / ip.incrementCpu)
			           * ip.incrementCpu
			           * ip.co2Cpu
			           * :reservedConcurrency
			          )
			       )
			    )
			  + (  CEIL(GREATEST(GREATEST(ip.minCpu, :cpu) * COALESCE(ip.minRamRatio, 0.0), :ram) / ip.incrementRam)
			     * ip.incrementRam
			     * (
			         (  ip.co2Ram
			          * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			          * :reservedConcurrency
			         )
			       + (
			           (  LEAST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                 * ip.incrementDuration
			                 * :nbRequests
			                 / :concurrencyMonth,
			                   :realConcurrency
			                 * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END
			              )
			         * ip.co2RamRequestConcurrency
			        )
			      + (  GREATEST(CEIL(GREATEST(ip.minDuration, :requestDuration) / ip.incrementDuration)
			                 * ip.incrementDuration
			                 * :nbRequests
			                 / :concurrencyMonth
			               -   :realConcurrency
			                 * CASE WHEN ip.period = 0 THEN (:rate*1.0) ELSE :rateFull END,
			                 0.0
			               )
			         * ip.co2RamRequest
			        )
			      )
			    )
			    )
			  + ( :nbRequests * ip.co2Requests ) AS monthlyCo2

			FROM ProvFunctionPrice ip WHERE
			     ip.location.id = :location
			 AND ip.incrementCpu IS NOT NULL
			 AND ip.incrementRam IS NOT NULL
			 AND (ip.maxCpu IS NULL or ip.maxCpu >= :cpu)
			 AND (ip.maxRam IS NULL OR ip.maxRam >= :ram)
			 AND (ip.maxDuration IS NULL OR ip.maxDuration >= :requestDuration)
			 AND (ip.maxRamRatio IS NULL OR GREATEST(ip.minCpu, :cpu) * ip.maxRamRatio <= :ram)
			 AND (ip.costRamRequestConcurrency = 0.0 AND :reservedConcurrency = 0.0 OR ip.costRamRequestConcurrency > 0.0 AND :reservedConcurrency > 0.0)
			 AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			 AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			""";

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types               The valid instance type identifiers.
	 * @param terms               The valid instance terms identifiers.
	 * @param cpu                 The required CPU.
	 * @param ram                 The consumed average RAM (GiB) during a month.
	 * @param location            The requested location identifier.
	 * @param rate                Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param globalRate          Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration            The duration in month. Minimum is 1.
	 * @param initialCost         The maximal initial cost.
	 * @param nbRequests          The monthly amount of executions of this function.
	 * @param realConcurrency     The actual concurrency.
	 * @param reservedConcurrency The concurrency to reserve.
	 * @param requestDuration     Average duration of a single request in milliseconds.
	 * @param concurrencyMonth    Constant value. Milliseconds per month per million requests.
	 * @param rateFull            Rate corresponding to full usage. Usually <code>1.0</code>.
	 * @param pageable            The page control to return few item.
	 * @return The cheapest price or empty result.
	 */
	@Query(DYNAMIC_QUERY + """
			 ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCost(List<Integer> types, List<Integer> terms, double cpu, double ram, int location,
			double rate, double globalRate, double duration, double initialCost, double nbRequests,
			double realConcurrency, double reservedConcurrency, double requestDuration, double concurrencyMonth,
			double rateFull, Pageable pageable);

	/**
	 * Return the lowest instance CO2 configuration from the minimal requirements.
	 *
	 * @param types               The valid instance type identifiers.
	 * @param terms               The valid instance terms identifiers.
	 * @param cpu                 The required CPU.
	 * @param ram                 The consumed average RAM (GiB) during a month.
	 * @param location            The requested location identifier.
	 * @param rate                Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param globalRate          Usage rate multiplied by the duration. Should be <code>rate * duration</code>.
	 * @param duration            The duration in month. Minimum is 1.
	 * @param initialCost         The maximal initial cost.
	 * @param nbRequests          The monthly amount of executions of this function.
	 * @param realConcurrency     The actual concurrency.
	 * @param reservedConcurrency The concurrency to reserve.
	 * @param requestDuration     Average duration of a single request in milliseconds.
	 * @param concurrencyMonth    Constant value. Milliseconds per month per million requests.
	 * @param rateFull            Rate corresponding to full usage. Usually <code>1.0</code>.
	 * @param pageable            The page control to return few item.
	 * @return The cheapest price or empty result.
	 */
	@Query(DYNAMIC_QUERY + """
			 ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC, ip.maxCpu ASC
			""")
	List<Object[]> findLowestDynamicCo2(List<Integer> types, List<Integer> terms, double cpu, double ram, int location,
			double rate, double globalRate, double duration, double initialCost, double nbRequests,
			double realConcurrency, double reservedConcurrency, double requestDuration, double concurrencyMonth,
			double rateFull, Pageable pageable);

	String LOWEST_QUERY = """
			SELECT
			 ip,
			  CASE
			   WHEN ip.period = 0 THEN (ip.cost * :rate * :duration)
			   ELSE (ip.costPeriod * CEIL(:duration/ip.period)) END AS totalCost,
			  CASE
			   WHEN ip.period = 0 THEN (ip.cost * :rate)
			   ELSE ip.cost END AS monthlyCost,
			 CASE
			  WHEN ip.period = 0 THEN (ip.co2 * :rate * :duration)
			  ELSE (ip.co2Period * CEIL(:duration/ip.period)) END AS totalCo2,
			 CASE
			  WHEN ip.period = 0 THEN (ip.co2 * :rate)
			  ELSE ip.co2 END AS monthlyCo2
			 FROM #{#entityName} ip  WHERE
			      ip.location.id = :location
			  AND ip.incrementCpu IS NULL
			  AND (ip.initialCost IS NULL OR :initialCost >= ip.initialCost)
			  AND (ip.type.id IN :types) AND (ip.term.id IN :terms)
			  AND (ip.maxDuration IS NULL OR ip.maxDuration >= :requestDuration)
			""";

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types           The valid instance type identifiers.
	 * @param terms           The valid instance terms identifiers.
	 * @param location        The requested location identifier.
	 * @param rate            Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration        The duration in month. Minimum is 1.
	 * @param initialCost     The maximal initial cost.
	 * @param requestDuration Average duration of a single request in milliseconds.
	 * @param pageable        The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCost ASC, totalCo2 ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCost(List<Integer> types, List<Integer> terms, int location, double rate, double duration,
			double initialCost, double requestDuration, Pageable pageable);

	/**
	 * Return the lowest instance price configuration from the minimal requirements.
	 *
	 * @param types           The valid instance type identifiers.
	 * @param terms           The valid instance terms identifiers.
	 * @param location        The requested location identifier.
	 * @param rate            Usage rate. Positive number. Maximum is <code>1</code>, minimum is <code>0.01</code>.
	 * @param duration        The duration in month. Minimum is 1.
	 * @param initialCost     The maximal initial cost.
	 * @param requestDuration Average duration of a single request in milliseconds.
	 * @param pageable        The page control to return few item.
	 * @return The minimum instance price or empty result.
	 */
	@Query(LOWEST_QUERY + """
			  ORDER BY totalCo2 ASC, totalCost ASC, ip.type.id DESC
			""")
	List<Object[]> findLowestCo2(List<Integer> types, List<Integer> terms, int location, double rate, double duration,
			double initialCost, double requestDuration, Pageable pageable);

}
