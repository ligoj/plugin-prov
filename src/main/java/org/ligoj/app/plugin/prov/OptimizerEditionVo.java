/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.bootstrap.core.NamedBean;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Optimizer definition inside a quote.
 */
@Getter
@Setter
@Accessors(chain = true)
public class OptimizerEditionVo extends NamedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	@NotNull
	private Optimizer mode;

}
