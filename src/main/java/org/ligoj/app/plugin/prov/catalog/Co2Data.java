/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import lombok.Getter;
import lombok.Setter;

/**
 * CO2 data.
 */
@Getter
@Setter
public class Co2Data {

	/**
	 * Ignored property
	 */
	private String drop;

	/**
	 * Instance type
	 */
	private String type;

	/**
	 * Instance Hourly Manufacturing Emissions (gCO‚CO2eq)
	 */
	private double value;
}
