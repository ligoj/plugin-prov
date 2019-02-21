/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Positive;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote instance query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class QuoteInstanceQuery extends AbstractQuoteInstanceQuery implements QuoteInstance {

	@DefaultValue(value = "LINUX")
	@QueryParam("os")
	@Builder.Default
	private VmOs os = VmOs.LINUX;

	@QueryParam("ephemeral")
	private boolean ephemeral;

	@QueryParam("software")
	private String software;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #minQuantity} or {@link #maxQuantity}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@QueryParam("maxVariableCost")
	@Positive
	private Double maxVariableCost;
}
