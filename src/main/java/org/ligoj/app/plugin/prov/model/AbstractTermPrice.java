/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.io.Serializable;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An priced database instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - (initialCost / 24 / 365)</code>.
 *
 * @param <T> Resource type.
 */
@Getter
@Setter
@ToString(of = { "term", "license" }, callSuper = true)
@MappedSuperclass
public abstract class AbstractTermPrice<T extends ProvType> extends AbstractPrice<T> implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The optional initial cost required to use this instance. May be <code>null</code>.
	 */
	private Double initialCost = 0d;

	/**
	 * The cost for the whole period defined in the term.
	 */
	private double costPeriod;

	/**
	 * Billing period duration in month. Any started period is due. When <code>0</code>, this assumes there is a billing
	 * period below 1 month. This value is a copy of
	 * {@link org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm#getPeriod()} value for performance purpose.
	 */
	private double period = 0;

	/**
	 * The optional monthly cost of one requested CPU. Not <code>null</code> for dynamic instance type.
	 */
	private Double costCpu;

	/**
	 * The optional monthly cost of one requested GB memory. Not <code>null</code> for dynamic instance type.
	 */
	private Double costRam;

	/**
	 * Increment CPU step. Not <code>null</code> for dynamic instance type.
	 */
	private Double incrementCpu;

	/**
	 * Minimal CPU. Not <code>null</code> for dynamic instance type.
	 */
	private Double minCpu;

	@NotNull
	@ManyToOne
	private ProvInstancePriceTerm term;

	/**
	 * Optional built-in license model. Special license term is <code>BYOL</code>. When <code>null</code>, license is
	 * included in the price or not applicable.
	 */
	private String license;

}
