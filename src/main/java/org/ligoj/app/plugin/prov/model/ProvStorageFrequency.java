package org.ligoj.app.plugin.prov.model;

/**
 * Storage access frequency class. Not represented as a table to be able to
 * compare the prices.
 */
public enum ProvStorageFrequency {

	/**
	 * Hot storage : optimized HDD or SSD
	 */
	HOT,

	/**
	 * Cold storage, low HDD or low frequency access
	 */
	COLD,

	/**
	 * Very Cold storage, low frequency access. May be object storage.
	 */
	ARCHIVE;

}
