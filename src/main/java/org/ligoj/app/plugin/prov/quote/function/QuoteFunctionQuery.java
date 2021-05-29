/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.function;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceQuery;
import org.ligoj.app.plugin.prov.model.QuoteFunction;

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
public class QuoteFunctionQuery extends AbstractQuoteInstanceQuery implements QuoteFunction {
	
	@Override
	public String getRuntime() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getDuration() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public long getNbRequests() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getConcurrency() {
		// TODO Auto-generated method stub
		return 0;
	}


}
