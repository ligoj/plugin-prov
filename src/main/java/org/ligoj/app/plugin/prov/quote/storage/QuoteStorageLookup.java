/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import org.ligoj.app.plugin.prov.AbstractLookup;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class QuoteStorageLookup extends AbstractLookup<ProvStoragePrice> {

	/**
	 * The requested size.
	 */
	private int size;
}
