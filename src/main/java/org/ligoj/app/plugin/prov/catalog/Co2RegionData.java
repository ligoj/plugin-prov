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
public class Co2RegionData {

	/**
	 * Ignored property
	 */
	private String drop;

	/**
	 * Region code name
	 */
	private String region;

	/**
	 * PUE
	 */
	private double pue;

	/**
	 * CO2e (metric gram/kWh)
	 */
	private double gPerKWH;

}
