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
	COST,

	/**
	 * Optimization by CO2 consumption (g)
	 */
	CO2;

}
