/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.function;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

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
	private String runtime = "Python";

	/**
	 * The average duration execution in milliseconds.
	 */
	@Positive
	private int duration = 100;

	/**
	 * The monthly amount of million executions of this function.
	 */
	@Positive
	private double nbRequests = 1;

	/**
	 * The average concurrency of this function. This parameter is hard to provides, and should correspond to a p99
	 * value and not the actual average. Can be lesser than 1.
	 */
	@PositiveOrZero
	private double concurrency = 0;
}
