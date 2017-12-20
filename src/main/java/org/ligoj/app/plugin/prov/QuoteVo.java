package org.ligoj.app.plugin.prov;

import java.util.List;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvUsage;
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
	private FloatingCost cost;

	/**
	 * Quoted instance.
	 */
	private List<ProvQuoteInstance> instances;

	/**
	 * Related storages instance.
	 */
	private List<ProvQuoteStorage> storages;
	
	/**
	 * Default location of this quote.
	 */
	private String location;
	
	/**
	 * Default usage of this quote. May be <code>null</code>.
	 */
	private ProvUsage usage;
}
