package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteInstanceEditionVo extends DescribedBean<Integer> {

	/**
	 * Instance price configuration
	 */
	@NotNull
	private Integer instancePrice;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;
}
