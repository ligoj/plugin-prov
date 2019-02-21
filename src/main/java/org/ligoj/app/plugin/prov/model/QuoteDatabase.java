/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote database query.
 */
public interface QuoteDatabase extends QuoteVm {

	/**
	 * Return the requested database engine.
	 *
	 * @return The requested database engine.
	 */
	String getEngine();

	/**
	 * Return the optional engine edition.
	 *
	 * @return Optional engine edition.
	 */
	String getEdition();

}
