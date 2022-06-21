/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.PositiveOrZero;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.QuoteVm;
import org.ligoj.app.plugin.prov.model.Rate;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote instance query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractQuoteInstanceQuery implements QuoteVm {

	@DefaultValue(value = "1")
	@QueryParam("cpu")
	@Builder.Default
	private double cpu = 1;

	@DefaultValue(value = "0")
	@QueryParam("gpu")
	@Builder.Default
	private double gpu = 0;

	/**
	 * Required memory in MiB.
	 */
	@DefaultValue(value = "1024")
	@QueryParam("ram")
	@Builder.Default
	private int ram = 1024;

	/**
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 *
	 * @see #cpu
	 */
	@PositiveOrZero
	@QueryParam("cpuMax")
	private Double cpuMax;

	/**
	 * The maximal used GPU. When <code>null</code>, the requested GPU is used.
	 *
	 * @see #gpu
	 */
	@PositiveOrZero
	@QueryParam("gpuMax")
	private Double gpuMax;

	/**
	 * The maximal used RAM. When <code>null</code>, the requested RAM is used.
	 *
	 * @see #ram
	 */
	@PositiveOrZero
	@QueryParam("ramMax")
	private Integer ramMax;

	@QueryParam("constant")
	private Boolean constant;

	/**
	 * Code of required instance type.
	 */
	@QueryParam("type")
	private String type;

	@QueryParam("location")
	private String location;

	@QueryParam("usage")
	private String usage;

	@QueryParam("budget")
	private String budget;

	@QueryParam("optimizer")
	private String optimizer;

	@QueryParam("license")
	private String license;

	/**
	 * Optional physical processor.
	 */
	@QueryParam("processor")
	private String processor;

	/**
	 * When <code>true</code>, this instance type is physical, not virtual.
	 */
	@QueryParam("physical")
	private Boolean physical;

	/**
	 * When <code>true</code>, this instance type must be executed at edge location.
	 */
	@QueryParam("edge")
	private Boolean edge;

	/**
	 * When <code>true</code> ephemeral instances are accepted.
	 */
	@QueryParam("ephemeral")
	private boolean ephemeral;

	/**
	 * Optional auto-scaling capability requirement.
	 */
	@QueryParam("autoScale")
	private boolean autoScale;

	@QueryParam("cpuRate")
	private Rate cpuRate;

	@QueryParam("gpuRate")
	private Rate gpuRate;

	@QueryParam("networkRate")
	private Rate networkRate;

	@QueryParam("storageRate")
	private Rate storageRate;

	@QueryParam("ramRate")
	private Rate ramRate;

	@Override
	public String getLocationName() {
		return getLocation();
	}

	@Override
	public String getUsageName() {
		return getUsage();
	}

	@Override
	public String getBudgetName() {
		return getBudget();
	}

	@Override
	public String getOptimizerName() {
		return getOptimizer();
	}
}
