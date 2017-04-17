package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvStorage;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration view.
 */
@Getter
@Setter
public class ProvQuoteStorageVo extends AbstractProvQuoteStorageVo {

	/**
	 * Related storage with the price.
	 */
	private ProvStorage storage;

}
