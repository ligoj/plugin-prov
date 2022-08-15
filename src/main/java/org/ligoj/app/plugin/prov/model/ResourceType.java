/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import lombok.Getter;

/**
 * A quote resource type.
 */
public enum ResourceType {

	/**
	 * Storage resource.
	 */
	STORAGE(true, false),

	/**
	 * Instance resource.
	 */
	INSTANCE(true, true),

	/**
	 * Support resource.
	 */
	SUPPORT(false, false),

	/**
	 * Database resource
	 */
	DATABASE(true, true),

	/**
	 * Container resource
	 */
	CONTAINER(true, true),

	/**
	 * Function resource
	 */
	FUNCTION(true, true);

	/**
	 * When <code>true</code>, has network capability.
	 */
	@Getter
	private boolean network;

	/**
	 * When <code>true</code>, has CO2 capability.
	 */
	@Getter
	private boolean co2;

	ResourceType(boolean network, boolean co2) {
		this.network = network;
		this.co2 = co2;
	}
}
