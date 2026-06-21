/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Test class of {@link BinPacker}
 */
class BinPackerTest {

	@Data
	@AllArgsConstructor
	static class Piece {
		double weight;
		String name;
	}

	private List<String> names(final List<Piece> pieces) {
		return pieces.stream().map(Piece::getName).toList();
	}

	@Test
	void packAllFit() {
		final var pieces = List.of(new Piece(2d, "a"), new Piece(5d, "b"), new Piece(1d, "c"));
		final var result = BinPacker.pack(pieces, Piece::getWeight, 10d);
		Assertions.assertEquals(List.of("b", "a", "c"), names(result.fitted()));
		Assertions.assertEquals(8d, result.total());
		Assertions.assertTrue(result.overflow().isEmpty());
	}

	@Test
	void packOverflow() {
		final var pieces = List.of(new Piece(2d, "a"), new Piece(5d, "b"), new Piece(10d, "c"), new Piece(1d, "d"),
				new Piece(7d, "e"), new Piece(17d, "f"), new Piece(21d, "g"), new Piece(9d, "h"));
		final var result = BinPacker.pack(pieces, Piece::getWeight, 40d);

		// Decreasing order: g(21), f(17), c(10), h(9), e(7), b(5), a(2), d(1)
		// g(21) + f(17) = 38, c(10) overflows, h(9) overflows, e(7) overflows, b(5) overflows, a(2) fits = 40,
		// d(1) overflows
		Assertions.assertEquals(List.of("g", "f", "a"), names(result.fitted()));
		Assertions.assertEquals(40d, result.total());
		Assertions.assertEquals(List.of("c", "h", "e", "b", "d"), names(result.overflow()));
	}

	@Test
	void packStableOrderForEqualWeights() {
		// Equal weights keep the given list's order
		final var pieces = List.of(new Piece(5d, "a"), new Piece(5d, "b"), new Piece(5d, "c"), new Piece(5d, "d"));
		final var result = BinPacker.pack(pieces, Piece::getWeight, 12d);
		Assertions.assertEquals(List.of("a", "b"), names(result.fitted()));
		Assertions.assertEquals(10d, result.total());
		Assertions.assertEquals(List.of("c", "d"), names(result.overflow()));
	}

	@Test
	void packNothingFits() {
		final var pieces = List.of(new Piece(2d, "a"), new Piece(5d, "b"));
		final var result = BinPacker.pack(pieces, Piece::getWeight, 1d);
		Assertions.assertTrue(result.fitted().isEmpty());
		Assertions.assertEquals(0d, result.total());
		Assertions.assertEquals(List.of("b", "a"), names(result.overflow()));
	}

	@Test
	void packEmpty() {
		final var result = BinPacker.pack(List.<Piece>of(), Piece::getWeight, 10d);
		Assertions.assertTrue(result.fitted().isEmpty());
		Assertions.assertEquals(0d, result.total());
		Assertions.assertTrue(result.overflow().isEmpty());
	}

	@Test
	void packExactCapacity() {
		final var pieces = List.of(new Piece(4d, "a"), new Piece(6d, "b"));
		final var result = BinPacker.pack(pieces, Piece::getWeight, 10d);
		Assertions.assertEquals(List.of("b", "a"), names(result.fitted()));
		Assertions.assertEquals(10d, result.total());
		Assertions.assertTrue(result.overflow().isEmpty());
	}
}
