package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The lowest price found for the requested resources.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class QuoteInstanceLookup extends AbstractComputedPrice<ProvInstancePrice> {

	// Nothing more than super class
}
