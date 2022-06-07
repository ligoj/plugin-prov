/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.ligoj.app.model.Configurable;
import org.ligoj.app.plugin.prov.Floating;

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
	double getCost();

	/**
	 * Return the computed maximal cost of this quoted element.
	 *
	 * @return The computed maximal cost of this quoted element.
	 */
	double getMaxCost();

	/**
	 * Indicates the {@link #getMaxCost()} is unbound.
	 *
	 * @return <code>true</code> when the {@link #getMaxCost()} is unbound.
	 */
	boolean isUnboundCost();

	/**
	 * Minimal initial cost.
	 *
	 * @return The computed initial cost of this quoted element.
	 */
	double getInitialCost();

	/**
	 * Maximal initial cost.
	 *
	 * @return The computed maximal initial cost of this quoted element.
	 */
	double getMaxInitialCost();

	/**
	 * Return the computed CO2 of this quoted element.
	 *
	 * @return The computed CO2 of this quoted element.
	 */
	double getCo2();

	/**
	 * Return the computed maximal CO2 of this quoted element.
	 *
	 * @return The computed maximal CO2 of this quoted element.
	 */
	double getMaxCo2();

	/**
	 * Return the {@link Floating} from the costs of this entity.
	 *
	 * @return The {@link Floating} from the costs of this entity.
	 */
	default Floating toFloating() {
		return new Floating(getCost(), getMaxCost(), getInitialCost(), getMaxInitialCost(), isUnboundCost(), getCo2(),
				getMaxCo2());
	}

}
