/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * First-fit-decreasing bin packing into a single capacity-constrained bin: pieces are sorted by descending weight
 * (stable sort, equal weights keep the given order), then greedily added to the bin while the capacity allows it. The
 * rejected pieces are collected in the overflow part of the result.
 */
public final class BinPacker {

	private BinPacker() {
		// Utility class, no instance
	}

	/**
	 * Packing result.
	 *
	 * @param <T>      The piece type.
	 * @param fitted   The pieces fitting in the bin, in packing order.
	 * @param total    The summed weight of the fitted pieces.
	 * @param overflow The pieces that did not fit in the bin, in packing order.
	 */
	public record Result<T>(List<T> fitted, double total, List<T> overflow) {
	}

	/**
	 * Pack the given pieces into a single bin having the given capacity using the first-fit-decreasing strategy.
	 *
	 * @param <T>      The piece type.
	 * @param pieces   The pieces to pack. Equal weights keep this list's order.
	 * @param weight   The weight provider of each piece.
	 * @param capacity The bin capacity.
	 * @return The packing result: fitted pieces, their total weight, and the overflowed pieces.
	 */
	public static <T> Result<T> pack(final List<T> pieces, final ToDoubleFunction<T> weight, final double capacity) {
		final var fitted = new ArrayList<T>();
		final var overflow = new ArrayList<T>();
		var total = 0d;
		for (final var piece : pieces.stream().sorted(Comparator.comparingDouble(weight).reversed()).toList()) {
			final var w = weight.applyAsDouble(piece);
			if (total + w <= capacity) {
				fitted.add(piece);
				total += w;
			} else {
				overflow.add(piece);
			}
		}
		return new Result<>(fitted, total, overflow);
	}
}
