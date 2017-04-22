package org.ligoj.app.plugin.prov;

import java.util.List;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.bootstrap.core.DescribedAuditedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * The complete data of a quote.
 */
@Getter
@Setter
public class QuoteVo extends DescribedAuditedBean<SimpleUserOrg, Integer> {

	/**
	 * Monthly cost, computed during the creation.
	 */
	private double cost;

	/**
	 * Quoted instance.
	 */
	private List<ProvQuoteInstance> instances;

	/**
	 * Related storages instance.
	 */
	private List<QuoteStorageVo> storages;
}
