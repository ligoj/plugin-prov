/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.RoundSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Floating cost configuration.
 */
@Getter
@AllArgsConstructor
public class FloatingPrice<P> {

	/**
	 * Minimal monthly cost.
	 */
	private FloatingCost cost;

	/**
	 * The maximal determined monthly cost. When the maximal cost cannot be determined, the minimal cost is used and the
	 * {@link #unbound} is set to <code>true</code>.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private P price;

}
