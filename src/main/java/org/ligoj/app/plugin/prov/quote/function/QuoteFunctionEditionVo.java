/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.function;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.AbstractQuoteVmEditionVo;
import org.ligoj.app.plugin.prov.model.QuoteFunction;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for a function while editing it.
 */
@Getter
@Setter
public class QuoteFunctionEditionVo extends AbstractQuoteVmEditionVo implements QuoteFunction {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;


	/**
	 * Return the requested runtime name. No version included.
	 */
	@NotBlank
	private String runtime;

	/**
	 * The average duration execution in milliseconds.
	 */
	@Positive
	private int duration;

	/**
	 * The monthly amount of executions of this function.
	 */
	@Positive
	private long nbRequests;

	/**
	 * The average concurrency of this function. This parameter is hard to provides, and should correspond to a p99
	 * value and not the actual average.
	 */
	@PositiveOrZero
	private int concurrency;
}
