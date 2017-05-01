package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvStorageType;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration view.
 */
@Getter
@Setter
public class QuoteStorageVo extends AbstractQuoteStorageVo {

	/**
	 * Related storage with the price.
	 */
	private ProvStorageType storage;

}
