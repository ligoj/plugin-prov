/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.Workload;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.model.ToIdSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource related to an instance and with floating cost.
 *
 * @param <P> Price configuration type.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuoteVm<P extends AbstractTermPriceVm<?>> extends AbstractQuote<P>
		implements QuoteVm, ResourceScope {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Included license.
	 */
	public static final String LICENSE_INCLUDED = "INCLUDED";

	/**
	 * The requested CPU.
	 */
	@PositiveOrZero
	private double cpu;

	/**
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 *
	 * @see #cpu
	 */
	@PositiveOrZero
	private Double cpuMax;

	/**
	 * The requested GPU.
	 */
	@PositiveOrZero
	private double gpu;

	/**
	 * The maximal used GPU. When <code>null</code>, the requested GPU is used.
	 *
	 * @see #gpu
	 */
	@PositiveOrZero
	private Double gpuMax;

	/**
	 * The requested RAM in "MiB". 1MiB = 1024 MiB.
	 */
	@PositiveOrZero
	private int ram;

	/**
	 * The maximal used RAM. When <code>null</code>, the requested RAM is used.
	 *
	 * @see #ram
	 */
	@PositiveOrZero
	private Integer ramMax;

	/**
	 * Efficient baseline CPU workload details.
	 * 
	 * @see Workload#from(String)
	 */
	private String workload;

	/**
	 * Optional physical processor.
	 */
	private String processor;

	/**
	 * When <code>true</code>, this instance type is physical, not virtual.
	 */
	private Boolean physical;

	/**
	 * When <code>true</code>, this instance type must be executed at edge location.
	 */
	private Boolean edge;

	/**
	 * The Internet access : Internet facing, etc.
	 */
	@NotNull
	private InternetAccess internet = InternetAccess.PUBLIC;

	/**
	 * The minimal quantity of this instance.
	 */
	@NotNull
	@PositiveOrZero
	private int minQuantity = 1;

	/**
	 * The maximal quantity of this instance. May be <code>null</code> when unbound maximal, otherwise must be greater
	 * than {@link #minQuantity}
	 */
	@PositiveOrZero
	private Integer maxQuantity = 1;

	/**
	 * Optional usage for this resource when different from the related quote.
	 */
	@ManyToOne
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvUsage usage;

	/**
	 * Optional budget for this resource when different from the related quote.
	 */
	@ManyToOne
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvBudget budget;

	/**
	 * Optional optimizer for this resource when different from the related quote.
	 */
	@ManyToOne
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvOptimizer optimizer;

	/**
	 * Optional license model. When <code>null</code>, the configuration license model will be used. May be
	 * {@value #LICENSE_INCLUDED}.
	 */
	private String license;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

	/**
	 * Optional auto-scaling capability requirement. When <code>true</code>, auto-scale must be supported.
	 */
	private boolean autoScale;

	/**
	 * Optional CPU rate requirement.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate cpuRate;

	/**
	 * Optional GPU rate requirement.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate gpuRate;

	/**
	 * Optional network rate requirement.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate networkRate;

	/**
	 * Optional storage rate requirement.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate storageRate;

	/**
	 * Optional RAM rate requirement.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate ramRate;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return maxQuantity == null;
	}

	/**
	 * Return attached storages.
	 *
	 * @return Attached storages.
	 */
	public abstract List<ProvQuoteStorage> getStorages();

	/**
	 * Return the effective usage applied to the given resource. May be <code>null</code>.
	 *
	 * @return The effective usage applied to the given resource. May be <code>null</code>.
	 */
	@JsonIgnore
	public ProvUsage getResolvedUsage() {
		return usage == null ? getConfiguration().getUsage() : usage;
	}

	/**
	 * Return the effective budget applied to the given resource. May be <code>null</code>.
	 *
	 * @return The effective budget applied to the given resource. May be <code>null</code>.
	 */
	@JsonIgnore
	public ProvBudget getResolvedBudget() {
		return budget == null ? getConfiguration().getBudget() : budget;
	}

	/**
	 * Return the effective optimizer applied to the given resource. May be <code>null</code>.
	 *
	 * @return The effective optimizer applied to the given resource. May be <code>null</code>.
	 */
	@JsonIgnore
	public ProvOptimizer getResolvedOptimizer() {
		return optimizer == null ? getConfiguration().getOptimizer() : optimizer;
	}

	/**
	 * Return the usage name applied to the given resource. May be <code>null</code>.
	 *
	 * @return The usage name applied to the given resource. May be <code>null</code>.
	 */
	@Override
	@JsonIgnore
	public String getUsageName() {
		return Optional.ofNullable(getResolvedUsage()).map(INamableBean::getName).orElse(null);
	}

	/**
	 * Return the budget name applied to the given resource. May be <code>null</code>.
	 *
	 * @return The budget name applied to the given resource. May be <code>null</code>.
	 */
	@Override
	@JsonIgnore
	public String getBudgetName() {
		return Optional.ofNullable(getResolvedBudget()).map(INamableBean::getName).orElse(null);
	}

	/**
	 * Return the optional optimizer name. May be <code>null</code> to use the default one.
	 *
	 * @return Optional optimizer name. May be <code>null</code> to use the default one.
	 */
	@Override
	@JsonIgnore
	public String getOptimizerName() {
		return Optional.ofNullable(getResolvedOptimizer()).map(INamableBean::getName).orElse(null);
	}

	/**
	 * Return the resolved location name applied to the given resource.
	 *
	 * @return The resolved location name applied to the given resource.
	 */
	@Override
	public String getLocationName() {
		return getResolvedLocation().getName();
	}

	@Override
	public abstract P getPrice();

}
