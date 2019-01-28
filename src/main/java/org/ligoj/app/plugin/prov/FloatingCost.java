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
@Getter
@Setter
@AllArgsConstructor
public class FloatingCost implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Minimal monthly cost.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double min;

	/**
	 * The maximal determined monthly cost. When the maximal cost cannot be determined, the minimal cost is used and the
	 * {@link #unbound} is set to <code>true</code>.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double max;

	/**
	 * When <code>true</code>, the maximal cost is not fully determined.
	 */
	private boolean unbound;

	/**
	 * Default float where {@link #min} and {@link #max} are set to <code>0</code>.
	 */
	public FloatingCost() {
		// No value
		this(0);
	}

	/**
	 * Constructor to define a fixed float.
	 *
	 * @param base
	 *            The minimal and maximal value.
	 */
	public FloatingCost(double base) {
		min = base;
		max = base;
	}

	/**
	 * Add a another floating cost. This operation updates the current object.
	 *
	 * @param other
	 *            Another cost.
	 * @return This object.
	 */
	public FloatingCost add(final FloatingCost other) {
		min += other.min;
		max += other.max;
		unbound |= other.unbound;
		return this;
	}

	/**
	 * Return a new instance with round values.
	 *
	 * @return A new instance with round values.
	 */
	public FloatingCost round() {
		return new FloatingCost(round(min), round(max), unbound);
	}

	private double round(final double value) {
		return Math.round(value * 1000d) / 1000d;
	}
}
