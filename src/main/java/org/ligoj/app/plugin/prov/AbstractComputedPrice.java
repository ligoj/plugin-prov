package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

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
	private double cost;

	/**
	 * The lowest price based price. May be <code>null</code>.
	 */
	private T price;
}
