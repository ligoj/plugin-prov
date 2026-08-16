/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Test class of {@link RoundSerializer}
 */
class RoundSerializerTest {

	@Getter
	@Setter
	static class Bean {
		@JsonSerialize(using = RoundSerializer.class)
		private Double nullable;

		@JsonSerialize(using = RoundSerializer.class)
		private double primary;
	}

	@Test
	void serializeNotNull() {
		final var bean = new Bean();
		bean.setNullable(1d);
		bean.setPrimary(2d);
		Assertions.assertEquals("{\"nullable\":1.0,\"primary\":2.0}", new ObjectMapper().writeValueAsString(bean));
	}

	@Test
	void serializeNull() {
		final var bean = new Bean();
		bean.setPrimary(2d);
		Assertions.assertEquals("{\"nullable\":null,\"primary\":2.0}", new ObjectMapper().writeValueAsString(bean));
	}
}
