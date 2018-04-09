package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote definition for edition. Identifier parameter is not used.
 */
@Getter
@Setter
public class QuoteEditionVo extends DescribedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Default location name when not defined at instance/storage level.
	 */
	@NotNull
	private String location;

	/**
	 * The usage name. May be <code>null</code> for a full usage.
	 */
	private String usage;

}
