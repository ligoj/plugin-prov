/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;

/**
 * A priced term based resource with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this resource. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - (initialCost / 24 / 365)</code>.
 *
 * @param <T> Resource type.
 */
@Getter
@Setter
@ToString(of = { "term" }, callSuper = true)
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
	 * The CO2 for the whole period defined in the term.
	 */
	@ColumnDefault("0")
	private double co2Period;

	/**
	 * Billing period duration in month. Any started period is due. When <code>0</code>, this assumes there is a billing
	 * period below 1 month. This value is a copy of {@link org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm}}
	 * <code>period</code> value for performance purpose.
	 */
	private double period = 0;

	@NotNull
	@ManyToOne
	private ProvInstancePriceTerm term;

}
