/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.QuoteDatabase;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote database query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QuoteDatabaseQuery extends AbstractQuoteInstanceQuery implements QuoteDatabase {

	@QueryParam("engine")
	private String engine;

	@QueryParam("edition")
	private String edition;

}
