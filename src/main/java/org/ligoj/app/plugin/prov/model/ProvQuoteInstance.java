/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
	@OneToMany(mappedBy = "quoteInstance", cascade = CascadeType.REMOVE)
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
