/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.apache.commons.lang3.ObjectUtils;

/**
 * VM Operating System. Not represented as a table to be able to compare the prices.
 */
public enum VmOs {

	/**
	 * Generic Linux like system, no cost
	 */
	LINUX(null),

	/**
	 * Microsoft Windows
	 */
	WINDOWS(null),

	/**
	 * Suse
	 */
	SUSE(null),

	/**
	 * Red Hat Enterprise
	 */
	RHEL(null),

	/**
	 * Centos
	 */
	CENTOS(LINUX),

	/**
	 * Debian
	 */
	DEBIAN(LINUX),

	/**
	 * Fedora
	 */
	FEDORA(LINUX),

	/**
	 * Ubuntu
	 */
	UBUNTU(LINUX),

	/**
	 * FREE BSD
	 */
	FREEBSD(LINUX);

	/**
	 * The related pricing OS.
	 */
	private final VmOs pricingOs;

	VmOs(VmOs pricingOs) {
		this.pricingOs = pricingOs;
	}

	/**
	 * Return the related pricing OS.
	 * @return the related pricing OS.
	 */
	public VmOs toPricingOs() {
		return ObjectUtils.defaultIfNull(pricingOs, this);
	}
}
