package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProvQuoteInstanceVo extends DescribedBean<Integer> {

	/**
	 * Instance price configuration
	 */
	@NotNull
	private Integer instance;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;
}
