/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceOsEditionVo;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.QuoteInstance;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for an instance while editing it.
 */
@Getter
@Setter
public class QuoteInstanceEditionVo extends AbstractQuoteInstanceOsEditionVo implements QuoteInstance {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Optional built-in software.
	 */
	private String software;

	/**
	 * The requested tenancy.
	 */
	private ProvTenancy tenancy = ProvTenancy.SHARED;
}
