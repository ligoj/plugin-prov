package org.ligoj.app.plugin.prov.model;

/**
 * Storage access latency class. Not represented as a table to be able to
 * compare the prices. Lowest ordinal corresponds to the highest latency.
 */
public enum ProvStorageLatency {

	/**
	 * Highest latency
	 */
	HIGHEST,

	/**
	 * High latency
	 */
	HIGH,

	/**
	 * Medium latency
	 */
	MEDIUM,

	/**
	 * Low latency
	 */
	LOW,

	/**
	 * Lowest (best) latency
	 */
	LOWEST;

}
