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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A configured function inside a quote. Name is unique inside a quote. The function cost does not include the
 * associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_FUNCTION", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteFunction extends AbstractQuoteVm<ProvFunctionPrice> implements QuoteFunction {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Return the requested runtime name. No version included.
	 */
	@NotNull
	@NotBlank
	private String runtime;

	/**
	 * The average duration execution in milliseconds.
	 */
	@Positive
	private int duration;

	/**
	 * The monthly amount of executions of this function.
	 */
	@Positive
	private long nbRequests;

	/**
	 * The average concurrency of this function. This parameter is hard to provides, and should correspond to a p99
	 * value and not the actual average.
	 */
	private int concurrency;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteFunction", cascade = CascadeType.REMOVE)
	private List<ProvQuoteStorage> storages;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvFunctionPrice price;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.FUNCTION;
	}
}
