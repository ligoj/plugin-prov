/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.container;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceOsEditionVo;
import org.ligoj.app.plugin.prov.model.QuoteContainer;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for a container while editing it.
 */
@Getter
@Setter
public class QuoteContainerEditionVo extends AbstractQuoteInstanceOsEditionVo implements QuoteContainer {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;
}
