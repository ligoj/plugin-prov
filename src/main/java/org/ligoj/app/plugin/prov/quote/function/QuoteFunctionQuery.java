/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.function;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceQuery;
import org.ligoj.app.plugin.prov.model.QuoteFunction;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote function query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QuoteFunctionQuery extends AbstractQuoteInstanceQuery implements QuoteFunction {

	@DefaultValue(value = "Python")
	@QueryParam("runtime")
	@Builder.Default
	private String runtime = "Python";

	@DefaultValue(value = "1")
	@QueryParam("nbRequests")
	@Builder.Default
	private double nbRequests = 1;

	@DefaultValue(value = "0")
	@QueryParam("concurrency")
	@Builder.Default
	private double concurrency = 0;

	@DefaultValue(value = "100")
	@QueryParam("duration")
	@Builder.Default
	private int duration = 100;

}
