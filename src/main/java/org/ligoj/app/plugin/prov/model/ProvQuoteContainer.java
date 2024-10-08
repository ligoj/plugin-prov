/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A configured container inside a quote. Name is unique inside a quote. The container cost does not include the
 * associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_CONTAINER", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteContainer extends AbstractQuoteVmOs<ProvContainerPrice> implements QuoteContainer {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #getMinQuantity()} or {@link #getMaxQuantity()}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteContainer")
	private List<ProvQuoteStorage> storages;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvContainerPrice price;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.CONTAINER;
	}
}
