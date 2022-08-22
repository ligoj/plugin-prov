/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * An priced function with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this function independent of the actual usage or
 * memory: pre-warmed resource. Includes the initial cost to allow quick sort. To compute the remaining monthly cost
 * reduced by the initial cost, the formula is : <code>cost - initialCost / 24 / 365</code>. Computation sample with
 * <code>nbRequests=1M, duration=1s, ram=1GiB</code>
 * <ul>
 * <li>AWS: max memory = 10, 240MiB, min memory = 128MiB, incrementRam=1MiB
 * <ul>
 * <li>Standard: 1M * costRequests + 1M * costRamRun * 1024MiB/1024 * 1s/(3600*24*30,5)</li>
 * <li>Provisioned: cost standard + (costCpu+costRam) * concurrency * ratio 1M * (costRequests + 1024MiB/1024 *
 * costRam{Provisioned Concurrency + Duration} * 1s/(1000*3600*24*30,5)); type = custom type</li>
 * </ul>
 * </li>
 * <li>Azure
 * <ul>
 * <li>Consumption: 1M * costRequests + 1024MiB/1024 * costRam * 1s/(3600*24*30,5)); max memory = 1.5GiB, min memory =
 * 128MiB, incrementRam= 128MiB</li>
 * <li>Premium: 1M * (costRequests + 1024/1024 * costRam{Provisioned Concurrency + Duration} *
 * 1s/(1000*3600*24*30,5))</li>
 * </ul>
 * </li>
 * </ul>
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_FUNCTION_PRICE", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "location", "license", "term", "type" }),
		@UniqueConstraint(columnNames = "code") })
public class ProvFunctionPrice extends AbstractTermPriceVm<ProvFunctionType> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Cost per millions requests.
	 */
	private double costRequests;

	/**
	 * Cost per GiB of RAM actually consumed during one month whatever the concurrency.
	 */
	private double costRamRequest;

	/**
	 * Cost per GiB of RAM actually consumed during one month within the concurrency limit.
	 */
	private double costRamRequestConcurrency;

	/**
	 * Increment of the billed duration of a single request.
	 */
	@Positive
	private double incrementDuration;

	/**
	 * Minimal billed duration of a single request. In milliseconds.
	 */
	private double minDuration;

	/**
	 * Maximal duration of a single request. In milliseconds.
	 */
	private Double maxDuration;

	/**
	 * CO2 per millions requests.
	 */
	private double co2Requests;

	/**
	 * CO2 per GiB of RAM actually consumed during one month whatever the concurrency.
	 */
	private double co2RamRequest;

	/**
	 * CO2 per GiB of RAM actually consumed during one month within the concurrency limit.
	 */
	private double co2RamRequestConcurrency;

}
