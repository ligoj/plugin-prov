/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource related to an instance and with floating cost and a OS.
 *
 * @param <P> Price configuration type.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuoteVmOs<P extends AbstractTermPriceVmOs<?>> extends AbstractQuoteVm<P>
		implements QuoteVmOs, ResourceScope {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #minQuantity} or {@link #maxQuantity}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

	/**
	 * The requested OS. May be different from the one related by {@link #price}, but refers to
	 * {@link VmOs#toPricingOs()}
	 */
	@NotNull
	private VmOs os;

}
