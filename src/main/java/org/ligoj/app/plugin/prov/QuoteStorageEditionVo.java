package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.Rate;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class QuoteStorageEditionVo extends AbstractQuoteStorageVo {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Optional required latency class.
	 */
	private Rate latency;

	/**
	 * Optional required optimized best usage of this storage
	 */
	private ProvStorageOptimized optimized;

	/**
	 * Optional instance compatibility flag.
	 */
	private Boolean instanceCompatible;

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
