package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A storage configuration inside a quote and optionally linked to an instance.
 * Name is unique inside a quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_STORAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteStorage extends AbstractDescribedEntity<Integer> implements Costed {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Size of the storage in "Go" "Giga Bytes"
	 */
	@NotNull
	private Integer size;

	/**
	 * The computed cost on the create/update time.
	 */
	@NotNull
	private Double cost;

	/**
	 * Related storage with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvStorage storage;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

	/**
	 * Optional linked quoted instance.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuoteInstance quoteInstance;

}
