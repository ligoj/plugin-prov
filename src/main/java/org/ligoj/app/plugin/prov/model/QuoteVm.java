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
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 * 
	 * @see #cpu
	 */
	Double getCpuMax();

	/**
	 * The maximal used RAM, in MiB. When <code>null</code>, the requested RAM is used.
	 * 
	 * @see #ram
	 */
	Integer getRamMax();

	/**
	 * Return Optional constant CPU. When <code>false</code>, variable CPU is requested. When <code>true</code> constant
	 * CPU is requested.
	 *
	 * @return Optional constant CPU. When <code>false</code>, variable CPU is requested. When <code>true</code>
	 *         constant CPU is requested.
	 */
	Boolean getConstant();

	/**
	 * Optional physical processor. Return Optional physical processor.
	 *
	 * @return Optional physical processor.
	 */
	String getProcessor();

	/**
	 * Optional physical constraint. When <code>true</code>, this instance type is physical, not virtual. Return
	 * Optional physical processor.
	 *
	 * @return Optional physical processor.
	 */
	Boolean getPhysical();

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
	 * Return optional license model. When <code>null</code>, the global quote's license is used.
	 *
	 * @return Optional license model. When <code>null</code>, the global quote's license is used.
	 */
	String getLicense();

	/**
	 * Return optional ephemeral constraint. When <code>false</code> (default), only non ephemeral instance are
	 * accepted. Otherwise (<code>true</code>), ephemeral instance contract is accepted.
	 *
	 * @return Optional ephemeral constraint. When <code>false</code> (default), only non ephemeral instance are
	 *         accepted. Otherwise (<code>true</code>), ephemeral instance contract is accepted.
	 */
	boolean isEphemeral();

	/**
	 * Return optional auto-scaling capability requirement.
	 *
	 * @return Optional auto-scaling capability requirement.
	 */
	boolean isAutoScale();


	 Rate getCpuRate();

	 Rate getNetworkRate();

	 Rate getStorageRate();

	 Rate getRamRate();

}
