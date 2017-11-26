package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote definition for edition.
 */
@Getter
@Setter
public class QuoteEditionVo extends DescribedBean<Integer> {

	/**
	 * Default location nae when not defined at instance/storage level.
	 */
	@NotNull
	private String location;

}
