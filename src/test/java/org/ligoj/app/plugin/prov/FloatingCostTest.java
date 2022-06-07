/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test class of {@link Floating}
 */
class FloatingTest {

	private static final double DELTA = 0.001;

	@Test
	void build() {
		final var cost = new Floating(1.1, 2.2, 0.4, 0.5, true, 10, 20);
		Assertions.assertEquals(1.1, cost.getMin());
		Assertions.assertEquals(2.2, cost.getMax());
		Assertions.assertEquals(0.4, cost.getInitial());
		Assertions.assertEquals(0.5, cost.getMaxInitial());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(1.1, cost.round().getMin());
		Assertions.assertEquals(2.2, cost.round().getMax());
		Assertions.assertTrue(cost.round().isUnbound());
		Assertions.assertEquals(10, cost.getMinCo2());
		Assertions.assertEquals(20, cost.getMaxCo2());

		cost.setMin(3.5000001);
		cost.setMax(4.5999999);
		cost.setMinCo2(5.5000001);
		cost.setMaxCo2(6.5999999);
		Assertions.assertEquals(3.5000001, cost.getMin());
		Assertions.assertEquals(4.5999999, cost.getMax());
		Assertions.assertEquals(5.5000001, cost.getMinCo2());
		Assertions.assertEquals(6.5999999, cost.getMaxCo2());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(3.5, cost.round().getMin());
		Assertions.assertEquals(4.6, cost.round().getMax());
		Assertions.assertEquals(5.5, cost.round().getMinCo2());
		Assertions.assertEquals(6.6, cost.round().getMaxCo2());
		Assertions.assertTrue(cost.round().isUnbound());
	}

	@Test
	void add() {
		final var cost1 = new Floating(1.1, 2.2, 0.1, 0.2, false, 10, 20);
		final var cost2 = new Floating(4.4, 5.5, 0.3, 0.4, false, 100, 200);
		final var cost3 = new Floating(6.6, 7.7, 0.5, 0.6, true, 1000, 2000);

		cost1.add(cost2);
		Assertions.assertEquals(5.5, cost1.getMin(), DELTA);
		Assertions.assertEquals(7.7, cost1.getMax(), DELTA);
		Assertions.assertEquals(0.4, cost1.getInitial(), DELTA);
		Assertions.assertEquals(0.6, cost1.getMaxInitial(), DELTA);
		Assertions.assertFalse(cost1.isUnbound());
		Assertions.assertEquals(110, cost1.getMinCo2(), DELTA);
		Assertions.assertEquals(220, cost1.getMaxCo2(), DELTA);

		cost1.add(cost3);
		Assertions.assertEquals(12.1, cost1.getMin(), DELTA);
		Assertions.assertEquals(15.4, cost1.getMax(), DELTA);
		Assertions.assertEquals(0.9, cost1.getInitial(), DELTA);
		Assertions.assertEquals(1.2, cost1.getMaxInitial(), DELTA);
		Assertions.assertTrue(cost1.isUnbound());
		Assertions.assertEquals(1110, cost1.getMinCo2(), DELTA);
		Assertions.assertEquals(2220, cost1.getMaxCo2(), DELTA);
	}
}
