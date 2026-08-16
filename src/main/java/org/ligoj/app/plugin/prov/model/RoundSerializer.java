/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.ligoj.app.plugin.prov.Floating;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

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
	public void serialize(final Double bean, final JsonGenerator generator, final SerializationContext provider) {
		generator.writeNumber(Floating.round(bean));
	}

}
