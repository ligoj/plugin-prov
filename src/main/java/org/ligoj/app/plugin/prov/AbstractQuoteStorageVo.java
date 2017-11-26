package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Base storage configuration view.
 */
@Getter
@Setter
public abstract class AbstractQuoteStorageVo extends DescribedBean<Integer> {

	/**
	 * Size of the storage in "GiB" "Gibi Bytes"
	 * 
	 * @see https://en.wikipedia.org/wiki/Gibibyte
	 */
	@Positive
	@NotNull
	private int size;

	/**
	 * Optional linked quoted instance.
	 */
	@Positive
	private Integer quoteInstance;

	/**
	 * Optional location constraint.
	 */
	private String location;
}
