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
		final FloatingCost cost = new FloatingCost(1.1, 2.2, true);
		Assertions.assertEquals(1.1, cost.getMin());
		Assertions.assertEquals(2.2, cost.getMax());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(1.0, cost.round().getMin());
		Assertions.assertEquals(2.0, cost.round().getMax());
		Assertions.assertTrue(cost.round().isUnbound());

		cost.setMin(3.5);
		cost.setMax(4.6);
		Assertions.assertEquals(3.5, cost.getMin());
		Assertions.assertEquals(4.6, cost.getMax());
		Assertions.assertTrue(cost.isUnbound());
		Assertions.assertEquals(4.0, cost.round().getMin());
		Assertions.assertEquals(5.0, cost.round().getMax());
		Assertions.assertTrue(cost.round().isUnbound());
	}
}
