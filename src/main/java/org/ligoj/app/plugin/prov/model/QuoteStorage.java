/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote storage query.
 */
public interface QuoteStorage {

	/**
	 * Return the requested size in GiB.
	 *
	 * @return The requested size in GiB.
	 */
	int getSize();

	/**
	 * Return the optional requested minimal {@link Rate} class.
	 *
	 * @return The optional requested minimal {@link Rate} class.
	 */
	Rate getLatency();

	/**
	 * Return the optional requested quote instance to be associated. Cannot be not <code>null</code> with
	 * {@link #getDatabase()} or {@link #getContainer()} or {@link #getFunction()}.
	 *
	 * @return The optional requested quote instance to be associated.
	 */
	Integer getInstance();

	/**
	 * Return the optional requested quote database to be associated. Cannot be not <code>null</code> with
	 * {@link #getInstance()} or {@link #getContainer()} or {@link #getFunction()}.
	 *
	 * @return The optional requested quote database to be associated.
	 */
	Integer getDatabase();

	/**
	 * Return the optional requested quote container to be associated. Cannot be not <code>null</code> with
	 * {@link #getInstance()} or {@link #getDatabase()} or {@link #getFunction()}.
	 *
	 * @return The optional requested quote database to be associated.
	 */
	Integer getContainer();

	/**
	 * Return the optional requested quote function to be associated. Cannot be not <code>null</code> with
	 * {@link #getInstance()} or {@link #getDatabase()} or {@link #getContainer()}.
	 *
	 * @return The optional requested quote function to be associated.
	 */
	Integer getFunction();

	/**
	 * The optional requested {@link ProvStorageOptimized}.
	 *
	 * @return The optional requested {@link ProvStorageOptimized}.
	 */
	ProvStorageOptimized getOptimized();

	/**
	 * Return Optional location name. May be <code>null</code>.
	 *
	 * @return The optional location name. May be <code>null</code>.
	 */
	String getLocationName();

}
