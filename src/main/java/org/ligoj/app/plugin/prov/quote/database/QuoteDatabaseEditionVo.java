/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.database;

import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.AbstractQuoteInstanceEditionVo;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for a database instance while editing it.
 */
@Getter
@Setter
public class QuoteDatabaseEditionVo extends AbstractQuoteInstanceEditionVo {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Database engine.
	 */
	@NotNull
	private String engine;

	/**
	 * Optional database edition.
	 */
	private String edition;
}
