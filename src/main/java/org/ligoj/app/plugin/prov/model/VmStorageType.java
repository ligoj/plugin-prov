package org.ligoj.app.plugin.prov.model;

/**
 * VM Storage type. Not represented as a table to be able to compare the prices.
 */
public enum VmStorageType {

	/**
	 * Hot
	 */
	HOT,
	/**
	 * Cold storage, magnetic
	 */
	COLD,

	/**
	 * Not instance storage type.
	 */
	OBJECT

}
