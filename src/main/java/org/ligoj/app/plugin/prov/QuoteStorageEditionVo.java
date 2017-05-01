package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class QuoteStorageEditionVo extends AbstractQuoteStorageVo {

	/**
	 * Related storage with the price.
	 */
	private int type;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;

}
