package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ComputedInstancePrice {

	/**
	 * The lowest instance based price. May be <code>null</code>.
	 */
	private ProvInstancePrice instance;

	/**
	 * The computed monthly cost of the related instance.
	 */
	private double cost;
}
