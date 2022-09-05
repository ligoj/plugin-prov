/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An priced instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - (initialCost / 24 / 365)</code>.
 *
 * @param <T> Resource type.
 */
@Getter
@Setter
@ToString(of = { "license" }, callSuper = true)
@MappedSuperclass
public abstract class AbstractTermPriceVm<T extends ProvType> extends AbstractTermPrice<T> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The optional monthly cost of one requested CPU. Required for dynamic instance type.
	 */
	private Double costCpu;

	/**
	 * The optional monthly cost of one requested GPU. Required for dynamic instance type.
	 */
	private Double costGpu;

	/**
	 * The optional monthly cost of one requested GiB memory. Required for dynamic instance type.
	 */
	private Double costRam;

	/**
	 * Increment CPU step. Required for dynamic instance type.
	 */
	private Double incrementCpu;

	/**
	 * Increment CPU step. Required for dynamic instance type.
	 */
	private Double incrementGpu;

	/**
	 * Increment RAM step (GiB). Required for dynamic instance type.
	 */
	private Double incrementRam;

	/**
	 * Minimal CPU. Required for dynamic instance type.
	 */
	private Double minCpu;

	/**
	 * Maximal CPU. Only valid for dynamic instance type.
	 */
	private Double maxCpu;

	/**
	 * Minimal GPU. Required for dynamic instance type.
	 */
	private Double minGpu;

	/**
	 * Maximal GPU. Only valid for dynamic instance type.
	 */
	private Double maxGpu;

	/**
	 * Optional minimal CPU to RAM (GiB) ratio.
	 */
	private Double minRamRatio;

	/**
	 * Optional maximal CPU to RAM (GiB) ratio.
	 */
	private Double maxRamRatio;

	/**
	 * Minimal memory in "GiB" = "Gigi Bytes". Required for dynamic instance type.
	 */
	private Double minRam;

	/**
	 * Maximal memory in "GiB" = "Gigi Bytes". Only valid for dynamic instance type.
	 */
	private Double maxRam;

	/**
	 * Optional built-in license model. Special license term is <code>BYOL</code>. When <code>null</code>, license is
	 * included in the price or not applicable.
	 */
	@Column(length = 50)
	private String license;

	/**
	 * The optional monthly CO2 consumption of one requested CPU with 100% workload usage. Required for dynamic instance
	 * type.
	 */
	@Column(columnDefinition = "double default 0")
	private double co2Cpu = 0d;

	/**
	 * The optional monthly CO2 consumption of one requested GPU with 100% workload usage. Required for dynamic instance
	 * type.
	 */
	@Column(columnDefinition = "double default 0")
	private double co2Gpu = 0d;

	/**
	 * The optional monthly CO2 consumption of one requested GiB memory with 100% workload usage. Required for dynamic
	 * instance type.
	 */
	@Column(columnDefinition = "double default 0")
	private double co2Ram = 0d;

	/**
	 * The optional monthly CO2 consumption of one requested CPU with an array of 10% workload usage, from idle to 90%.
	 * Required for dynamic instance type.
	 */
	private String co2Cpu10 = null;

	/**
	 * The optional monthly CO2 consumption of one requested GPU with an array of 10% workload usage, from idle to 90%.
	 * Required for dynamic instance type.
	 */
	private String co2Gpu10 = null;

	/**
	 * The optional monthly CO2 consumption of one requested GiB memory with an array of 10% workload usage, from idle
	 * to 90%. Required for dynamic instance type.
	 */
	private String co2Ram10 = null;

}
