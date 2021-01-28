/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.container;

import javax.validation.constraints.Positive;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceEditionVo;
import org.ligoj.app.plugin.prov.model.QuoteContainer;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for a container while editing it.
 */
@Getter
@Setter
public class QuoteContainerEditionVo extends AbstractQuoteInstanceEditionVo implements QuoteContainer {

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
	 * {@link #minQuantity} or {@link #maxQuantity}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;
}
