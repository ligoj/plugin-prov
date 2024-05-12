/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.Serializable;

import org.ligoj.app.plugin.prov.model.RoundSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Floating cost configuration.
 */
@AllArgsConstructor
@Getter
public class Floating implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Minimal monthly cost.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	@Setter
	private double min;

	/**
	 * The maximal determined monthly cost. When the maximal cost cannot be determined, the minimal cost is used and the
	 * {@link #unbound} is set to <code>true</code>.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	@Setter
	private double max;

	/**
	 * Minimal initial cost.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double initial = 0d;

	/**
	 * Maximal initial cost.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double maxInitial = 0d;

	/**
	 * When <code>true</code>, the maximal cost is not fully determined.
	 */
	private boolean unbound;

	/**
	 * Minimal monthly CO2 consumption.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	@Setter
	private double minCo2;

	/**
	 * The maximal determined monthly cost. When the maximal CO2 consumption. cannot be determined, the minimal CO2
	 * consumption. is used and the {@link #unbound} is set to <code>true</code>.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	@Setter
	private double maxCo2;

	/**
	 * Default float where {@link #min} and {@link #max} are set to <code>0</code>.
	 */
	public Floating() {
		// No value
		this(0,0);
	}

	/**
	 * Constructor to define a fixed float.
	 *
	 * @param base The minimal and maximal value.
	 * @param baseCo2 The minimal and maximal CO2 value.
	 */
	public Floating(final double base, final double baseCo2) {
		min = base;
		max = base;
		minCo2 = baseCo2;
		maxCo2 = baseCo2;
	}

	/**
	 * Add another floating cost. This operation updates the current object.
	 *
	 * @param other Another cost.
	 * @return This object.
	 */
	public Floating add(final Floating other) {
		min += other.min;
		max += other.max;
		initial += other.initial;
		maxInitial += other.maxInitial;
		unbound |= other.unbound;

		minCo2 += other.minCo2;
		maxCo2 += other.maxCo2;
		return this;
	}

	/**
	 * Add another detailed cost. This operation updates the current object.
	 *
	 * @param cost Another cost.
	 * @param co2 Another co2.
	 * @return This object.
	 */
	public Floating add(final double cost, final double co2) {
		min += cost;
		max += cost;
		minCo2 += co2;
		maxCo2 += co2;
		return this;
	}

	/**
	 * Multiply this cost by a given rate. This operation updates the current object.
	 *
	 * @param rate Rate to apply.
	 * @return This object.
	 */
	public Floating multiply(final double rate) {
		min *= rate;
		max *= rate;
		initial *= rate;
		maxInitial *= rate;

		minCo2 *= rate;
		maxCo2 *= rate;
		return this;
	}


	/**
	 * Return a new instance with round values.
	 *
	 * @return A new instance with round values.
	 */
	public Floating round() {
		return new Floating(round(min), round(max), initial, maxInitial, unbound, round(minCo2), round(maxCo2));
	}

	/**
	 * Round up to 3 decimals the given value.
	 *
	 * @param value Raw value.
	 * @return The rounded value.
	 */
	public static double round(final double value) {
		return Math.round(value * 1000d) / 1000d;
	}
}
