/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Storage usage optimization.
 */
public enum ProvStorageOptimized {

	/**
	 * Throughput optimized storage.
	 */
	THROUGHPUT,

	/**
	 * IOPS optimized storage
	 */
	IOPS,

	/**
	 * Durability over performance
	 */
	DURABILITY

}
