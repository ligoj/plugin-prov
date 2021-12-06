/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;
import java.util.Map;

import org.ligoj.app.iam.SimpleUserOrg;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvNetwork;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
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
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Monthly cost, including support.
	 */
	private FloatingCost cost;

	/**
	 * Monthly cost without support.
	 */
	private FloatingCost costNoSupport;

	/**
	 * Monthly support cost, based on {@link #cost}.
	 */
	private FloatingCost costSupport;

	/**
	 * The optional Terraform status.
	 */
	private TerraformStatus terraformStatus;

	/**
	 * Quoted instances.
	 */
	private List<ProvQuoteInstance> instances;

	/**
	 * Quoted containers.
	 */
	private List<ProvQuoteContainer> containers;
	/**
	 * Quoted functions.
	 */
	private List<ProvQuoteFunction> functions;

	/**
	 * Quoted databases.
	 */
	private List<ProvQuoteDatabase> databases;

	/**
	 * Related storages.
	 */
	private List<ProvQuoteStorage> storages;

	/**
	 * Related supports instance.
	 */
	private List<ProvQuoteSupport> supports;

	/**
	 * All available locations.
	 */
	private List<ProvLocation> locations;

	/**
	 * Tags attached to resources in this quote. The second {@link Map}'s key corresponds to the resource identifier of
	 * the parent resource type.
	 */
	private Map<ResourceType, Map<Integer, List<TagVo>>> tags;

	/**
	 * Networks attached to resources in this quote.
	 */
	private List<ProvNetwork> networks;

	/**
	 * Default location of this quote.
	 */
	private ProvLocation location;

	/**
	 * Default usage of this quote. May be <code>null</code>.
	 */
	private ProvUsage usage;

	/**
	 * Default budget of this quote. May be <code>null</code>.
	 */
	private ProvBudget budget;

	/**
	 * Default license model. May be <code>null</code>, equivalent to 'INCLUDED'.
	 */
	private String license;

	/**
	 * Rate applied to required RAM to lookup the suiting instance type. This rate is divided by <code>100</code>, then
	 * multiplied to the required RAM of each memory before calling the lookup. Values lesser than <code>100</code>
	 * allows the lookup to elect an instance having less RAM than the requested one. Value greater than
	 * <code>100</code> makes the lookup to request instance types providing more RAM than the requested one.
	 */
	private int ramAdjustedRate = 100;

	/**
	 * Reservation mode.
	 */
	private ReservationMode reservationMode = ReservationMode.RESERVED;

	/**
	 * Optional physical processor. May be <code>null</code>.
	 */
	private String processor;

	/**
	 * Optional physical host requirement. May be <code>null</code>. When <code>true</code>, this instance type is
	 * physical, not virtual.
	 */
	private Boolean physical;

	/**
	 * Optional currency. When <code>null</code>, the currency is <code>USD</code> with <code>1</code> rate.
	 */
	private ProvCurrency currency;

	/**
	 * All usages associated to this quote.
	 */
	private List<ProvUsage> usages;

	/**
	 * All budgets associated to this quote.
	 */
	private List<ProvBudget> budgets;

	/**
	 * All valid processors for this subscription.
	 */
	private Map<String, List<String>> processors;

	/**
	 * UI settings. Properties are: 
	 * <ul>
	 * <li>
	 * Attached tags colors mapping as a JSON map. Key is the tag name. Value is the color code. Color name is not
	 * accepted. Sample: <code>#e4560f</code> or <code>rgb(255, 0, 0)</code>, <code>hsl(0, 100%, 50%)</code>.</li>
	 * </ul>
	 */
	private String uiSettings;

}
