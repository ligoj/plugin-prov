package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.bootstrap.core.NamedAuditedBean;

public class QuoteVo extends NamedAuditedBean<SimpleUserOrg, Integer> {

	/**
	 * Monthly cost, computed during the creation.
	 */
	@NotNull
	@Min(0)
	private Double cost;

	// TODO Add instances, storage,...
}
