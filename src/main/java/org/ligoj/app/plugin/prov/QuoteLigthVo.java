package org.ligoj.app.plugin.prov;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteLigthVo extends DescribedBean<Integer> {

	/**
	 * The amount of instances.
	 */
	private int nbInstances;

	/**
	 * The size of the global storage in Giga Bytes.
	 */
	private int totalStorage;

	/**
	 * The amount of storages devices.
	 */
	private int nbStorages;

	/**
	 * The amount of CPU
	 */
	private int totalCpu;

	/**
	 * The amount of memory (MB)
	 */
	private int totalRam;

	/**
	 * The computed monthly cost.
	 */
	private double cost;

}
