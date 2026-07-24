/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvLookupError;

import lombok.Getter;
import lombok.Setter;

/**
 * One compared subscription (CS) in a comparison set: its identifier, quote name, aggregate cost / CO2 (carried by the
 * {@link Floating}) and the resources it could not reproduce from the main subscription.
 */
@Getter
@Setter
public class ComparedSubscriptionVo {

	/**
	 * The compared subscription (CS) identifier.
	 */
	private int subscription;

	/**
	 * The CS quote name.
	 */
	private String name;

	/**
	 * The CS aggregate cost (its {@code co2}/{@code maxCo2} fields carry the carbon totals).
	 */
	private Floating cost;

	/**
	 * The MS resources this CS could not reproduce (no matching offer).
	 */
	private List<ProvLookupError> errors;
}
