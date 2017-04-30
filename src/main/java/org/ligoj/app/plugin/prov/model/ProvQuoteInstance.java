package org.ligoj.app.plugin.prov.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A configured instance inside a quote. Name is unique inside a quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_INSTANCE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteInstance extends AbstractDescribedEntity<Integer> implements Costed {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Related instance with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvInstancePrice instancePrice;

	/**
	 * The requested CPU.
	 */
	@NotNull
	private Double cpu;

	/**
	 * The computed cost on the create/update time.
	 */
	@NotNull
	private Double cost;

	/**
	 * The requested RAM in MB.
	 */
	@NotNull
	private Integer ram;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteInstance", cascade = CascadeType.REMOVE)
	private List<ProvQuoteStorage> storages;

}
