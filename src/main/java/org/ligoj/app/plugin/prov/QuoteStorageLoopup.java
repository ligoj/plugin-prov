package org.ligoj.app.plugin.prov;

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
public class QuoteStorageLoopup extends AbstractComputedPrice<ProvStoragePrice> {

	/**
	 * The requested size.
	 */
	private int size;
}
