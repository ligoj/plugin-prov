package org.ligoj.app.plugin.prov;

import java.util.List;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.bootstrap.core.DescribedAuditedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteVo extends DescribedAuditedBean<SimpleUserOrg, Integer> {

	/**
	 * Quoted instance.
	 */
	private List<ProvQuoteInstance> instances;

	/**
	 * Related storages instance.
	 */
	private List<QuoteStorageVo> storages;
}
