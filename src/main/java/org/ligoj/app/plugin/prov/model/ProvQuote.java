/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.model.PluginConfiguration;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvisioningService;
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
public class ProvQuote extends AbstractDescribedAuditedEntity<Integer>
		implements PluginConfiguration, Costed, ResourceScope {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Minimal monthly cost, computed during the creation and kept synchronized with the updates. Includes support cost.
	 */
	@NotNull
	@PositiveOrZero
	private double cost = 0d;

	/**
	 * Maximal determined monthly cost, computed during the creation and kept synchronized with the updates. Includes
	 * support cost. When there are unbound maximal quantities (<code>unboundCostCounter &gt; 0</code>), the
	 * {@link #unboundCostCounter} is incremented. Otherwise, this value is equals to {@link #cost}
	 */
	@NotNull
	@PositiveOrZero
	private double maxCost = 0d;

	/**
	 * Minimal initial cost. Does not includes support cost.
	 */
	@PositiveOrZero
	private double initialCost = 0d;

	/**
	 * Maximal initial cost. Does not includes support cost.
	 * 
	 * @see #maxCost
	 */
	@PositiveOrZero
	private double maxInitialCost = 0d;

	/**
	 * Minimal monthly support cost, computed during the creation and kept synchronized with the updates.
	 */
	@PositiveOrZero
	@NotNull
	private Double costSupport = 0d;

	/**
	 * Maximal monthly support cost, computed during the creation and kept synchronized with the updates.
	 */
	@PositiveOrZero
	@NotNull
	private Double maxCostSupport = 0d;

	/**
	 * Minimal monthly cost without cost, computed during the creation and kept synchronized with the updates.
	 */
	@PositiveOrZero
	@NotNull
	private Double costNoSupport = 0d;

	/**
	 * Maximal monthly cost without cost, computed during the creation and kept synchronized with the updates.
	 */
	@PositiveOrZero
	@NotNull
	private Double maxCostNoSupport = 0d;

	/**
	 * The amount unbound maximal quantities. Would be used to track the unbound monthly bills on different scenarios.
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
	 * Quoted databases.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteDatabase> databases;

	/**
	 * Usages associated to this quote..
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvUsage> usages = new ArrayList<>();

	/**
	 * Budgets associated to this quote..
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvBudget> budgets = new ArrayList<>();

	/**
	 * Quoted storages.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteStorage> storages;

	/**
	 * Quoted supports.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteSupport> supports = new ArrayList<>();

	/**
	 * Attached tags.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvTag> tags;

	/**
	 * Default location constraint.
	 */
	@ManyToOne
	@NotNull
	private ProvLocation location;

	/**
	 * Optional default usage. When <code>null</code>, full time.
	 */
	@ManyToOne
	private ProvUsage usage;

	/**
	 * Optional default budget. When <code>null</code>, full time.
	 */
	@ManyToOne
	private ProvBudget budget;

	/**
	 * Optional license model. <code>null</code> value corresponds to {@value ProvQuoteInstance#LICENSE_INCLUDED}.
	 */
	private String license;

	/**
	 * Rate applied to required RAM to lookup the suiting instance type. This rate is divided by <code>100</code>, then
	 * multiplied to the required RAM of each memory before calling the lookup. Value lesser than <code>100</code>
	 * allows the lookup to elect an instance having less RAM than the requested one. Value greater than
	 * <code>100</code> makes the lookup to request instance types providing more RAM than the requested one.
	 */
	@Min(50)
	@Max(150)
	private Integer ramAdjustedRate = 100;

	/**
	 * Optional reservation mode. By default is {@link org.ligoj.app.plugin.prov.model.ReservationMode#RESERVED}
	 */
	private ReservationMode reservationMode = ReservationMode.RESERVED;

	/**
	 * Optional currency. When <code>null</code>, the currency is <code>USD</code> with <code>1</code> rate.
	 */
	@ManyToOne
	private ProvCurrency currency;

	/**
	 * Optional processor. May be <code>null</code>.
	 */
	private String processor;

	/**
	 * Optional physical host requirement. May be <code>null</code>. When <code>true</code>, this instance type is
	 * physical, not virtual.
	 */
	private Boolean physical;

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

	/**
	 * Resolved service instance. May be <code>null</code> while unresolved.
	 */
	@Transient
	@JsonIgnore
	private ProvisioningService service;

	/**
	 * When <code>true</code>, the lean process is executed after each change. This option implies more computations
	 * when there are budgets with associated initial cost.
	 */
	private Boolean leanOnChange = false;
}
