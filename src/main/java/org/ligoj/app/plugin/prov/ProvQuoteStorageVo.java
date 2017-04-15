package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvStorage;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration view.
 */
@Getter
@Setter
public class ProvQuoteStorageVo {
	
	/**
	 * Storage configuration identifier.
	 */
	private int id;

	/**
	 * Size of the storage in "Go" "Giga Bytes"
	 */
	private int size;

	/**
	 * Related storage with the price.
	 */
	private ProvStorage storage;

	/**
	 * Optional linked quoted instance.
	 */
	private Integer instance;

}
