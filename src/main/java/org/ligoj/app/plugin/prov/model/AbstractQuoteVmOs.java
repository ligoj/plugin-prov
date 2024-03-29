/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource related to an instance and with floating cost and an OS.
 *
 * @param <P> Price configuration type.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuoteVmOs<P extends AbstractTermPriceVmOs<?>> extends AbstractQuoteVm<P>
		implements QuoteVmOs {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #getMinQuantity()} or {@link #getMaxQuantity()}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

	/**
	 * The requested OS. May be different from the one related by {@link #getPrice()}, but refers to
	 * {@link VmOs#toPricingOs()}
	 */
	@NotNull
	private VmOs os;

}
