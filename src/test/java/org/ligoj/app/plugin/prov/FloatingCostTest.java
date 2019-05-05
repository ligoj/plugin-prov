/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test class of {@link FloatingCost}
 */
public class FloatingCostTest {

	@Test
	public void build() {
		final var cost = new FloatingCost(1.1, 2.2, true);
		Assertions.assertEquals(1.1, cost.getMin());
		Assertions.assertEquals(2.2, cost.getMax());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(1.1, cost.round().getMin());
		Assertions.assertEquals(2.2, cost.round().getMax());
		Assertions.assertTrue(cost.round().isUnbound());

		cost.setMin(3.5000001);
		cost.setMax(4.5999999);
		Assertions.assertEquals(3.5000001, cost.getMin());
		Assertions.assertEquals(4.5999999, cost.getMax());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(3.5, cost.round().getMin());
		Assertions.assertEquals(4.6, cost.round().getMax());
		Assertions.assertTrue(cost.round().isUnbound());
	}
	@Test
	public void add() {
		final var cost1 = new FloatingCost(1.1, 2.2, false);
		final var cost2 = new FloatingCost(4.4, 5.5, false);
		final var cost3 = new FloatingCost(6.6, 7.7, false);
		cost3.setUnbound(true);

		cost1.add(cost2);
		Assertions.assertEquals(5.5, cost1.getMin());
		Assertions.assertEquals(7.7, cost1.getMax());
		Assertions.assertFalse(cost1.isUnbound());

		cost1.add(cost3);
		Assertions.assertEquals(12.1, cost1.getMin());
		Assertions.assertEquals(15.4, cost1.getMax());
		Assertions.assertTrue(cost1.isUnbound());
	}
}
