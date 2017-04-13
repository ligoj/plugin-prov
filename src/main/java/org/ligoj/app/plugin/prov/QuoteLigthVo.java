package org.ligoj.app.plugin.prov;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.bootstrap.core.NamedAuditedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteLigthVo extends NamedAuditedBean<SimpleUserOrg, Integer> {

	/**
	 * The amount of instances.
	 */
	private int nbInstances;

	/**
	 * The size of the global storage in Giga Bytes.
	 */
	private int totalStorage;

	/**
	 * The amount of CPU
	 */
	private int totalCpu;

	/**
	 * The amount of memory (MB)
	 */
	private int totalMemory;

	/**
	 * The computed monthly cost.
	 */
	private double cost;

}
