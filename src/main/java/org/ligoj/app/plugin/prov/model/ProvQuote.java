package org.ligoj.app.plugin.prov.model;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractDescribedAuditedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A saved quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE")
public class ProvQuote extends AbstractDescribedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Monthly cost, computed during the creation.
	 */
	@NotNull
	@Min(0)
	private Double cost;

	/**
	 * The related subscription.
	 */
	@NotNull
	@ManyToOne
	private Subscription subscription;

	/**
	 * Quoted instance.
	 */
	@OneToMany(mappedBy = "quote")
	@JsonIgnore
	private List<ProvQuoteInstance> instances;

	/**
	 * Quoted storages.
	 */
	@OneToMany(mappedBy = "quote")
	@JsonIgnore
	private List<ProvQuoteStorage> storages;

}
