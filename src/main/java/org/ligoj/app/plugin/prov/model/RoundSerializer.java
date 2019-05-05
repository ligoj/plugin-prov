package org.ligoj.app.plugin.prov.model;

import java.io.IOException;

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
	public static final RoundSerializer INSTANCE = new RoundSerializer();

	protected RoundSerializer() {
		super(Double.class, false);
	}

	@Override
	public void serialize(final Double bean, final JsonGenerator generator, final SerializerProvider provider)
			throws IOException {
		generator.writeNumber(Math.round(bean * 1000d) / 1000d);
	}

}
