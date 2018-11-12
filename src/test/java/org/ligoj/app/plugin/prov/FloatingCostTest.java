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
}
