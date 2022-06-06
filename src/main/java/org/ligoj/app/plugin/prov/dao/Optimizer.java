/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import lombok.Getter;

/**
 * Optimizer type of lookups
 */
@Getter
public enum Optimizer {

	/**
	 * Optimization by cost (USD)
	 */
	COST("totalCost", "totalCo2"),

	/**
	 * Optimization by CO2 consumption (g)
	 */
	CO2("totalCo2", "totalCost");

	/**
	 * The primary ascending sorted property name.
	 */
	private final String orderPrimary;

	/**
	 * The secondary ascending sorted property name.
	 */
	private final String orderSecondary;

	Optimizer(final String orderPrimary, final String orderSecondary) {
		this.orderPrimary = orderPrimary;
		this.orderSecondary = orderSecondary;
	}

}
