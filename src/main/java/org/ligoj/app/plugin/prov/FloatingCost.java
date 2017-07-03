package org.ligoj.app.plugin.prov;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Floating cost configuration.
 */
@Getter
@Setter
@AllArgsConstructor
public class FloatingCost {

	/**
	 * Default float where {@link #min} and {@link #max} are set to
	 * <code>0</code>.
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
	 * Minimal monthly cost.
	 */
	private double min;

	/**
	 * The maximal determined monthly cost. When the maximal cost cannot be
	 * determined, the minimal cost is used and the {@link #unbound} is set to
	 * <code>true</code>.
	 */
	private double max;

	/**
	 * When <code>true</code>, the maximal cost is not fully determined.
	 */
	private boolean unbound;

}
