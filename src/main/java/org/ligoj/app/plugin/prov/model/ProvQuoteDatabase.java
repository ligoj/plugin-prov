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
 * A configured database instance inside a quote. Name is unique inside a quote. The instance cost does not include the
 * associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_DATABASE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteDatabase extends AbstractQuoteVm<ProvDatabasePrice> implements QuoteDatabase {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Optional database engine.
	 */
	private String engine;

	/**
	 * Optional edition.
	 */
	private String edition;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteDatabase", cascade = CascadeType.REMOVE)
	private List<ProvQuoteStorage> storages;

	/**
	 * Resolved database price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvDatabasePrice price;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.DATABASE;
	}
}
