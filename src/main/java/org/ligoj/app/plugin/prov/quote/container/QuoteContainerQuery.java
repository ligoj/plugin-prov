/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.container;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceQuery;
import org.ligoj.app.plugin.prov.model.QuoteContainer;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote container query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QuoteContainerQuery extends AbstractQuoteInstanceQuery implements QuoteContainer {

	@DefaultValue(value = "LINUX")
	@QueryParam("os")
	@Builder.Default
	private VmOs os = VmOs.LINUX;

}
