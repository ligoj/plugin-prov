/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.support;

import org.ligoj.app.plugin.prov.AbstractLookup;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class QuoteSupportLookup extends AbstractLookup<ProvSupportPrice> {

	private Integer seats;
}
