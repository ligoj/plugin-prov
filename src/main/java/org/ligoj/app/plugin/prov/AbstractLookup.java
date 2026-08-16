/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.ProvType;
import org.ligoj.app.plugin.prov.model.RoundSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * The computed price for the requested resources.
 *
 * @param <T> The price type.
 */
@Getter
@Setter
@ToString
public abstract class AbstractLookup<T extends AbstractPrice<? extends ProvType>> {

	/**
	 * The computed monthly cost of the related resource.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double cost;

	/**
	 * The computed monthly co2 of the related resource.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double co2;

	/**
	 * The lowest price based price. May be <code>null</code>.
	 */
	private T price;
}
