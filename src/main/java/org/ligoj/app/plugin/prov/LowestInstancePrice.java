package org.ligoj.app.plugin.prov;

import lombok.Getter;
import lombok.Setter;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
public class LowestInstancePrice {

	/**
	 * The lowest instance based price. May be <code>null</code>.
	 */
	private ComputedInstancePrice instance;

	/**
	 * The lowest custom instance based price. May be <code>null</code>.
	 */
	private ComputedInstancePrice custom;
}
