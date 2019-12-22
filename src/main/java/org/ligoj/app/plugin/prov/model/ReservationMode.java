/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Resource reservation mode.
 */
public enum ReservationMode {

	/**
	 * Resource reserved as currently, or as initially required.
	 */
	RESERVED,

	/**
	 * When available, use the maximal observed used resources.
	 */
	MAX
}
