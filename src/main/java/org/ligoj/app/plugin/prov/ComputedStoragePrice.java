package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvStorageType;

import lombok.Getter;
import lombok.Setter;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
public class ComputedStoragePrice extends AbstractComputedPrice {

	/**
	 * The lowest storage type price.
	 */
	private ProvStorageType type;
	
	/**
	 * The requested size.
	 */
	private int size;
}
