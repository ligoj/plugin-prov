/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

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
	 * The optional monthly cost of one requested GB memory. Required for dynamic instance type.
	 */
	private Double costRam;

	/**
	 * Increment CPU step. Required for dynamic instance type.
	 */
	private Double incrementCpu;

	/**
	 * Increment RAM step. Required for dynamic instance type.
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
	private String license;

}
