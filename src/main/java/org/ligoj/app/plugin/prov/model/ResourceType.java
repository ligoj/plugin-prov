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
	STORAGE(true),

	/**
	 * Instance resource.
	 */
	INSTANCE(true),

	/**
	 * Support resource.
	 */
	SUPPORT(false),

	/**
	 * Database resource
	 */
	DATABASE(true),

	/**
	 * Container resource
	 */
	CONTAINER(true),

	/**
	 * Function resource
	 */
	FUNCTION(true);

	@Getter
	private boolean network;

	ResourceType(boolean network) {
		this.network = network;
	}
}
