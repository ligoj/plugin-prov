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
 * Workload modelisation.
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

	static Workload from(final String rawData) {
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
