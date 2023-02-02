/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.validation.constraints.Positive;

import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for an VM OS based while editing it.
 */
@Getter
@Setter
public abstract class AbstractQuoteInstanceOsEditionVo extends AbstractQuoteVmEditionVo {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The requested OS. May be <code>null</code> and defaulted to the #instancePrice 's OS.
	 */
	private VmOs os;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #getMinQuantity()} or {@link #getMaxQuantity()}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

}
