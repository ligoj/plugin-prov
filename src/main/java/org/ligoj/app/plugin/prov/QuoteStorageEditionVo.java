/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class QuoteStorageEditionVo extends DescribedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Size of the storage in "GiB" "Gibi Bytes"
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Gibibyte">Gibibyte</a>
	 */
	@Positive
	@NotNull
	private int size;

	/**
	 * Optional linked quoted instance.
	 */
	private Integer quoteInstance;

	/**
	 * Optional location constraint.
	 */
	private String location;

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
