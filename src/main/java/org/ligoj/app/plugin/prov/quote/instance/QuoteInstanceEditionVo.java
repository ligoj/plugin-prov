/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import javax.validation.constraints.Positive;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceEditionVo;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for an instance while editing it.
 */
@Getter
@Setter
public class QuoteInstanceEditionVo extends AbstractQuoteInstanceEditionVo implements QuoteInstance {

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

	/**
	 * Optional built-in software.
	 */
	private String software;

	/**
	 * The requested tenancy.
	 */
	private ProvTenancy tenancy = ProvTenancy.SHARED;
}
