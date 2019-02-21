/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * The lowest price found for the requested database resources.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class QuoteDatabaseLookup extends AbstractLookup<ProvDatabasePrice> {

	// Nothing more than super class
}
