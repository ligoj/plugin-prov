/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceQuery;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
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

	@QueryParam("software")
	private String software;

	@DefaultValue(value = "SHARED")
	@QueryParam("tenancy")
	@Builder.Default
	private ProvTenancy tenancy = ProvTenancy.SHARED;
}
