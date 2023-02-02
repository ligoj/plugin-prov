/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.validation.constraints.PositiveOrZero;

import org.ligoj.bootstrap.core.NamedBean;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Budget definition inside a quote.
 */
@Getter
@Setter
@Accessors(chain = true)
public class BudgetEditionVo extends NamedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The maximal accepted initial cost. When <code>null</code>, is <code>0</code>.
	 */
	@PositiveOrZero
	private double initialCost = 0;

}
