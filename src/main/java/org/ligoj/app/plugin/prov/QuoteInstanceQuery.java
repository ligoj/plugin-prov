/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote instance query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QuoteInstanceQuery extends AbstractQuoteInstanceQuery implements QuoteInstance {

	@DefaultValue(value = "LINUX")
	@QueryParam("os")
	@Builder.Default
	private VmOs os = VmOs.LINUX;

	@QueryParam("ephemeral")
	private boolean ephemeral;

	@QueryParam("software")
	private String software;
}
