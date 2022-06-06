/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.io.IOException;

import org.ligoj.app.plugin.prov.Floating;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * Return the rounded value in JSON.
 */
public class RoundSerializer extends StdSerializer<Double> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * JAX-RS serializer instance.
	 */
	public static final RoundSerializer INSTANCE = new RoundSerializer();

	protected RoundSerializer() {
		super(Double.class, false);
	}

	@Override
	public void serialize(final Double bean, final JsonGenerator generator, final SerializerProvider provider)
			throws IOException {
		generator.writeNumber(Floating.round(bean));
	}

}
