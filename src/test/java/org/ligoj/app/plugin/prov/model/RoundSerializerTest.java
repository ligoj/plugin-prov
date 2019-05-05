/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/**
 * Test class of {@link RoundSerializer}
 */
public class RoundSerializerTest {

	@Getter
	@Setter
	public static class Bean {
		@JsonSerialize(using = RoundSerializer.class)
		private Double nullable;

		@JsonSerialize(using = RoundSerializer.class)
		private double primary;
	}

	@Test
	public void serializeNotNull() throws JsonProcessingException {
		final var bean = new Bean();
		bean.setNullable(1d);
		bean.setPrimary(2d);
		Assertions.assertEquals("{\"nullable\":1.0,\"primary\":2.0}", new ObjectMapper().writeValueAsString(bean));
	}


	@Test
	public void serializeNull() throws JsonProcessingException {
		final var bean = new Bean();
		bean.setPrimary(2d);
		Assertions.assertEquals("{\"nullable\":null,\"primary\":2.0}", new ObjectMapper().writeValueAsString(bean));
	}
}
