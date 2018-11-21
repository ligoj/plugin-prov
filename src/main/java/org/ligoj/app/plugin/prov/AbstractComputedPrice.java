/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.RoundSerializer;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The computed price for the requested resources.
 */
@Getter
@Setter
@ToString
public abstract class AbstractComputedPrice<T extends AbstractPrice<? extends AbstractPersistable<?>>> {

	/**
	 * The computed monthly cost of the related resource.
	 */
	@JsonSerialize(using = RoundSerializer.class)
	private double cost;

	/**
	 * The lowest price based price. May be <code>null</code>.
	 */
	private T price;
}
