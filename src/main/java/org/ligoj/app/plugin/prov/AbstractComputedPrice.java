package org.ligoj.app.plugin.prov;

import lombok.Getter;
import lombok.Setter;

/**
 * The computed price for the requested resources.
 */
@Getter
@Setter
public abstract class AbstractComputedPrice {

	/**
	 * The computed monthly cost of the related resource.
	 */
	private double cost;
}
