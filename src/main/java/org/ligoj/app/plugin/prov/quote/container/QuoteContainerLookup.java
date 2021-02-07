/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.container;

import org.ligoj.app.plugin.prov.AbstractLookup;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;

import lombok.Getter;
import lombok.Setter;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
public class QuoteContainerLookup extends AbstractLookup<ProvContainerPrice> {

	// Nothing more than super class
}
