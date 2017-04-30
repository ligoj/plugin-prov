package org.ligoj.app.plugin.prov.model;

import org.ligoj.app.model.Configurable;

/**
 * A configured and also costed entity.
 *
 */
public interface Costed extends Configurable<ProvQuote, Integer> {

	/**
	 * Return the computed cost of this quoted element.
	 * @return The computed cost of this quoted element.
	 */
	Double getCost();
}
