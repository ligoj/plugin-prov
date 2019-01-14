/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * A configured instance inside a quote. Name is unique inside a quote. The instance cost does not include the
 * associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_INSTANCE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteInstance extends AbstractQuoteResourceInstance<ProvInstancePrice> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The requested OS. May be different from the one related by {@link #price}, but refers to
	 * {@link VmOs#toPricingOs()}
	 */
	@NotNull
	private VmOs os;

	/**
	 * The Internet access : Internet facing, etc.
	 */
	@NotNull
	private InternetAccess internet = InternetAccess.PUBLIC;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #minQuantity} or {@link #maxQuantity}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

	/**
	 * Optional software.
	 */
	private String software;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvInstancePrice price;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.INSTANCE;
	}
}
