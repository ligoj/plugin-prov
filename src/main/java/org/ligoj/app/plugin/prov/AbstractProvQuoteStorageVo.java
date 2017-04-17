package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Min;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Base storage configuration view.
 */
@Getter
@Setter
public abstract class AbstractProvQuoteStorageVo extends DescribedBean<Integer> {

	/**
	 * Size of the storage in "Go" "Giga Bytes"
	 */
	@Min(1)
	private int size;

	/**
	 * Optional linked quoted instance.
	 */
	private Integer instance;

}
