package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.jnellis.binpack.LinearBin;
import net.jnellis.binpack.LinearBinPacker;

class BinPackerTest {

	@Data
	@AllArgsConstructor
	static class Piece {
		Double internalId;
		String name;
	}

	@Test
	void test() {
		final var values = new ArrayList<>(List.of(2d, 5d, 10d, 1d, 7d, 1d, 17d, 21d, 9d, 18d, 5d, 12d));
		final var piecesToItems = new IdentityHashMap<Double, Piece>();
		final var index = new AtomicInteger(0);
		values.forEach(v -> piecesToItems.put(v, new Piece(v, "name" + index.incrementAndGet())));
		final var packer = new LinearBinPacker();
		final var existingBins = new ArrayList<>(List.of(new LinearBin(1600d)));
		final var bins = packer.packAll(values, existingBins, new ArrayList<>(List.of(Double.MAX_VALUE)));
		System.out.println(bins);
		bins.getFirst().getPieces().forEach(p -> System.out.println(piecesToItems.get(p)));
	}
}
