/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Efficient baseline with detailed CPU workload.
 */
@Getter
public class Workload {

	private final double baseline;
	private final List<WorkloadPeriod> periods;

	/**
	 * Default baseline value.
	 */
	private static final double DEFAULT_BASELINE = 5d;

	/**
	 * Default workload profile.
	 */
	private static final Workload DEFAULT_WORKLOAD = new Workload(DEFAULT_BASELINE,
			new String[] { null, "100@" + DEFAULT_BASELINE });

	/**
	 * Full workload profile.
	 */
	private static final Workload FULL_WORKLOAD = new Workload(100, new String[] { null, "100@100" });

	private Workload(double baseline, String[] parts) {
		this.baseline = baseline;
		if (parts.length == 1) {
			this.periods = List.of(new WorkloadPeriod(100, baseline));
		} else {
			this.periods = Stream.of(parts).skip(1).map(p -> {
				final var periodParts = StringUtils.split(p, '@');
				return new WorkloadPeriod(Double.parseDouble(periodParts[0]), Double.parseDouble(periodParts[1]));
			}).toList();
		}
	}

	@AllArgsConstructor
	class WorkloadPeriod {
		double duration = 0d;
		double value = 0d;
	}

	/**
	 * This string follows this pattern: <code>$baseline(,$duration@$cpu)*</code>. Sample value:
	 * <code>80,20@55,10@23,65@10</code>. Constraints:
	 * <ul>
	 * <li>Weighted average of values corresponds to the baseline</li>
	 * <li>When there is no details (only the baseline), no computation has to be done. Min is <code>0</code> and max is
	 * <code>100</code></li>.
	 * <li>Sum of durations should be <code>100</code>. When different than <code>100</code> a prorata is applied to
	 * each value according to their weight.</li>
	 * <li>Min value's duration is <code>0</code></li>
	 * <li>Max value's duration should be <code>100</code>, however adjustment is applied to be aligned to
	 * <code>100</code></li>
	 * <li>Min value's COU is <code>0</code></li>
	 * <li>Max value's CPU is <code>100</code></li>
	 * <ul>
	 * 
	 * @param rawData The raw data containing baseline and optional details.
	 * @return The Workload entity built from this raw value.
	 */
	public static Workload from(final String rawData) {
		if (rawData == null) {
			return DEFAULT_WORKLOAD;
		}
		final var parts = StringUtils.split(rawData, ',');
		final var baseline = Double.parseDouble(parts[0]);
		if (baseline == 100d) {
			return FULL_WORKLOAD;
		}
		return new Workload(baseline, parts);
	}
}
