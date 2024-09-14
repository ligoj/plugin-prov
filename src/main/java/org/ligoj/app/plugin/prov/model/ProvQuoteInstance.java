/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * A configured instance inside a quote. Name is unique inside a quote. The instance cost does not include the
 * associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_INSTANCE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteInstance extends AbstractQuoteVmOs<ProvInstancePrice> implements QuoteInstance {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Optional software.
	 */
	private String software;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteInstance")
	private List<ProvQuoteStorage> storages;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvInstancePrice price;

	/**
	 * The requested tenancy.
	 */
	private ProvTenancy tenancy = ProvTenancy.SHARED;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.INSTANCE;
	}
}
