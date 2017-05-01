package org.ligoj.app.plugin.prov.model;

/**
 * VM Storage class. Not represented as a table to be able to compare the prices.
 */
public enum ProvStorageType {

	/**
	 * Hot storage : optimized HDD or SSD
	 */
	HOT,

	/**
	 * Cold storage, low HDD or low frequency access
	 */
	COLD,

	/**
	 * Not instance storage type.
	 */
	OBJECT

}
