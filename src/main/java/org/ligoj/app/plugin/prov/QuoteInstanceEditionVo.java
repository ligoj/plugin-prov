package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteInstanceEditionVo extends DescribedBean<Integer> {

	/**
	 * Instance price configuration matching to the requirements.
	 */
	@NotNull
	private Integer instancePrice;
	
	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;
	
	/**
	 * The requested CPU
	 */
	@NotNull
	private Double cpu;
	
	/**
	 * The requested memory in MB.
	 */
	@NotNull
	private Integer ram;

	/**
	 * The requested CPU behavior. When <code>false</code>, the CPU is variable, with boost mode.
	 */
	private Boolean constant;
	
}
