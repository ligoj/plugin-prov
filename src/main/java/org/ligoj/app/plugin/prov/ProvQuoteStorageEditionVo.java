package org.ligoj.app.plugin.prov;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class ProvQuoteStorageEditionVo extends AbstractProvQuoteStorageVo {

	/**
	 * Related storage with the price.
	 */
	private int storage;

}
