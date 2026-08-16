/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.RoundSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * Floating cost configuration.
 *
 * @param <P> The price term type.
 */
@Getter
@AllArgsConstructor
public class FloatingPrice<P extends AbstractTermPrice<?>> {

	/**
	 * Minimal monthly cost.
	 */
	private Floating cost;

	/**
	 * The related price instance.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private P price;

}
