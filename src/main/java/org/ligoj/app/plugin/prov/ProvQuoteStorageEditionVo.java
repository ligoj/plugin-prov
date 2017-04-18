package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class ProvQuoteStorageEditionVo extends AbstractProvQuoteStorageVo {

	/**
	 * Related storage with the price.
	 */
	private int storage;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;

}
