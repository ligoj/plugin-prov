/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote query.
 */
public interface QuoteVm {

	/**
	 * Return the amount of required CPU. Default is 1.
	 *
	 * @return The amount of required CPU. Default is 1.
	 */
	double getCpu();

	/**
	 * Return the amount of required RAM, in MiB. Default is 1.
	 *
	 * @return The amount of required RAM, in MiB. Default is 1.
	 */
	int getRam();

	/**
	 * Return the amount of required GPU. Default is 0.
	 *
	 * @return The amount of required GPU. Default is 0.
	 */
	double getGpu();

	/**
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 *
	 * @return The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 * @see #getCpu()
	 */
	Double getCpuMax();

	/**
	 * The maximal used GPU. When <code>null</code>, the requested GPU is used.
	 *
	 * @return The maximal used GPU. When <code>null</code>, the requested GPU is used.
	 * @see #getGpu()
	 */
	Double getGpuMax();

	/**
	 * The maximal used RAM, in MiB. When <code>null</code>, the requested RAM is used.
	 *
	 * @return The maximal used RAM, in MiB. When <code>null</code>, the requested RAM is used.
	 * @see #getRam()
	 */
	Integer getRamMax();

	/**
	 * The typical workload of this resource using repeated CPU baseline and patterns data points.
	 *
	 * @return Optional global workload. Default is full-time 100%.
	 */
	String getWorkload();

	/**
	 * Optional physical processor. Return Optional physical processor.
	 *
	 * @return Optional physical processor.
	 */
	String getProcessor();

	/**
	 * Optional physical constraint. When <code>true</code>, this instance type is physical, not virtual.
	 *
	 * @return Optional physical processor.
	 */
	Boolean getPhysical();

	/**
	 * Optional edge constraint. When <code>true</code>, this instance type must be executed at edge location.
	 *
	 * @return Optional edge constraint.
	 */
	Boolean getEdge();

	/**
	 * Return optional instance type name. May be <code>null</code>.
	 *
	 * @return Optional instance type name. May be <code>null</code>.
	 */
	default String getType() {
		return null;
	}

	/**
	 * Return the optional location name. When <code>null</code>, the global quote's location is used.
	 *
	 * @return Optional location name. When <code>null</code>, the global quote's location is used.
	 */
	String getLocationName();

	/**
	 * Return the optional usage name. May be <code>null</code> to use the default one.
	 *
	 * @return Optional usage name. May be <code>null</code> to use the default one.
	 */
	String getUsageName();

	/**
	 * Return the optional budget name. May be <code>null</code> to use the default one.
	 *
	 * @return Optional budget name. May be <code>null</code> to use the default one.
	 */
	String getBudgetName();

	/**
	 * Return the optional optimizer name. May be <code>null</code> to use the default one.
	 *
	 * @return Optional optimizer name. May be <code>null</code> to use the default one.
	 */
	String getOptimizerName();

	/**
	 * Return optional license model. When <code>null</code>, the global quote's license is used.
	 *
	 * @return Optional license model. When <code>null</code>, the global quote's license is used.
	 */
	String getLicense();

	/**
	 * Return optional ephemeral constraint. When <code>false</code> (default), only non-ephemeral instance are
	 * accepted. Otherwise (<code>true</code>), ephemeral instance contract is accepted.
	 *
	 * @return Optional ephemeral constraint. When <code>false</code> (default), only non-ephemeral instance are
	 *         accepted. Otherwise (<code>true</code>), ephemeral instance contract is accepted.
	 */
	boolean isEphemeral();

	/**
	 * Return optional auto-scaling capability requirement.
	 *
	 * @return Optional auto-scaling capability requirement.
	 */
	boolean isAutoScale();

	/**
	 * Minimal required CPU rate
	 *
	 * @return CPU rate
	 */
	Rate getCpuRate();

	/**
	 * Minimal required GPU rate
	 *
	 * @return GPU rate
	 */
	Rate getGpuRate();

	/**
	 * Minimal required network rate
	 *
	 * @return network rate
	 */
	Rate getNetworkRate();

	/**
	 * Minimal required storage rate
	 *
	 * @return storage rate
	 */
	Rate getStorageRate();

	/**
	 * Minimal required RAM rate
	 *
	 * @return RAM rate
	 */
	Rate getRamRate();

}
