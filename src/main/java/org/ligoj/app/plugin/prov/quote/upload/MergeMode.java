/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.upload;

/**
 * Merge mode.
 */
public enum MergeMode {

	/**
	 * The existing entries will be updated with the not null values of this file.
	 */
	UPDATE,

	/**
	 * The existing entries are not touched, the new entries with the same name use counters as suffix.
	 */
	KEEP,

	/**
	 * Inserting an entry with the same name causes an error.
	 */
	INSERT

}
