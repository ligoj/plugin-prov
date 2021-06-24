/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote function query.
 */
public interface QuoteFunction extends QuoteVm {

	/**
	 * Return the requested runtime name. No version included.
	 *
	 * @return The requested runtime name. No version included.
	 */
	String getRuntime();

	/**
	 * The average duration execution in milliseconds.
	 *
	 * @return The average duration execution in milliseconds.
	 */
	int getDuration();

	/**
	 * The amount of requests per month.
	 *
	 * @return The amount of requests per month.
	 */
	double getNbRequests();

	/**
	 * The average concurrency of this function. This parameter is hard to provides, and should correspond to a p99
	 * value and not the actual average. Can be lesser than 0.
	 *
	 * @return The average concurrency of this function.
	 */
	double getConcurrency();

}
