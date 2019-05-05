/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * The light view of a quote with aggregated information for display purpose.
 */
@Getter
@Setter
public class QuoteLightVo extends DescribedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The amount (minimum quantity) of instances.
	 */
	private int nbInstances;

	/**
	 * The amount (minimum quantity) of databases.
	 */
	private int nbDatabases;

	/**
	 * The size of the global storage in Giga Bytes.
	 */
	private int totalStorage;

	/**
	 * The amount of storages devices.
	 */
	private int nbStorages;

	/**
	 * The amount of instances with Internet/public access. Corresponds to amount a required public IP.
	 */
	private int nbPublicAccess;

	/**
	 * The amount of CPU
	 */
	private double totalCpu;

	/**
	 * The amount of memory (MB)
	 */
	private int totalRam;

	/**
	 * The computed monthly cost.
	 */
	private FloatingCost cost;

	/**
	 * The optional currency
	 */
	private ProvCurrency currency;

	/**
	 * The main location of this quote.
	 */
	private ProvLocation location;

}
