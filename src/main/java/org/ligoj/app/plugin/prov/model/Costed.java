/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.ligoj.app.model.Configurable;

/**
 * A configured and also costed entity.
 *
 */
public interface Costed extends Configurable<ProvQuote, Integer> {

	/**
	 * Return the computed cost of this quoted element.
	 * 
	 * @return The computed cost of this quoted element.
	 */
	Double getCost();

	/**
	 * Return the computed max cost of this quoted element.
	 * 
	 * @return The computed max cost of this quoted element.
	 */
	Double getMaxCost();

	/**
	 * Indicates the {@link #getMaxCost()} is unbound.
	 * 
	 * @return <code>true</code> when the {@link #getMaxCost()} is unbound.
	 */
	boolean isUnboundCost();
}
