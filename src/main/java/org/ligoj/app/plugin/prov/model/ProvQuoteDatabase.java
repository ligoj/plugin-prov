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
	@OneToMany(mappedBy = "quoteDatabase")
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
