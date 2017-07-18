package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ProvStorageType;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration view.
 */
@Getter
@Setter
public class QuoteStorageVo extends AbstractQuoteStorageVo {

	/**
	 * Related storage type with the price.
	 */
	private ProvStorageType type;

	/**
	 * The computed cost on the create/update time.
	 */
	@NotNull
	private Double cost;
	private Double maxCost;

}
