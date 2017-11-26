package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class QuoteStorageEditionVo extends AbstractQuoteStorageVo {

	/**
	 * Related storage type name within the given location.
	 */
	@NotNull
	private String type;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	@Positive
	private Integer subscription;

}
