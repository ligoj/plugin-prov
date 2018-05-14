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
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.model.PluginConfiguration;
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
public class ProvQuote extends AbstractDescribedAuditedEntity<Integer> implements PluginConfiguration, Costed {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Minimal monthly cost, computed during the creation and kept synchronized with
	 * the updates.
	 */
	@NotNull
	@PositiveOrZero
	private Double cost = 0d;

	/**
	 * Maximal determined monthly cost, computed during the creation and kept
	 * synchronized with the updates. When there are unbound maximal quantities
	 * (<code>null</code>), the minimal cost is used and the
	 * {@link #unboundCostCounter} is incremented.
	 */
	@NotNull
	@PositiveOrZero
	private Double maxCost = 0d;

	/**
	 * The amount unbound maximal quantities. Would be used to track the unbound
	 * monthly bills on different scenarios.
	 */
	@NotNull
	@PositiveOrZero
	private Integer unboundCostCounter = 0;

	/**
	 * The related subscription.
	 */
	@NotNull
	@ManyToOne
	@JsonIgnore
	private Subscription subscription;

	/**
	 * Quoted instance.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteInstance> instances;

	/**
	 * Usages associated to this quote..
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvUsage> usages;

	/**
	 * Quoted storages.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteStorage> storages;

	/**
	 * Default location constraint.
	 */
	@ManyToOne
	@NotNull
	private ProvLocation location;

	/**
	 * Optional usage. When <code>null</code>, full time.
	 */
	@ManyToOne
	private ProvUsage usage;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return unboundCostCounter > 0;
	}

	@Override
	@JsonIgnore
	public ProvQuote getConfiguration() {
		return this;
	}
}
