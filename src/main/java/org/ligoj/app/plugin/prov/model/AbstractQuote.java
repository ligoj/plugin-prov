/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource with floating cost.
 *
 * @param <P> Price configuration type.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuote<P extends AbstractPrice<?>> extends AbstractDescribedEntity<Integer>
		implements Costed {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The minimal computed monthly cost of the resource.
	 */
	@NotNull
	@PositiveOrZero
	private double cost;

	/**
	 * Maximal determined monthly cost of the resource. When there is an unbound maximal (<code>null</code>) quantity,
	 * the minimal cost is used.
	 */
	@NotNull
	@PositiveOrZero
	private double maxCost;

	/**
	 * Minimal initial cost. Does not includes support cost.
	 */
	@NotNull
	@PositiveOrZero
	private double initialCost = 0d;

	/**
	 * Maximal initial cost. Does not includes support cost.
	 *
	 * @see #maxCost
	 */
	@NotNull
	@PositiveOrZero
	private double maxInitialCost = 0d;
	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

	/**
	 * Optional expected location for this resource.
	 */
	@ManyToOne
	private ProvLocation location;

	/**
	 * Return resolved price configuration.
	 *
	 * @return Resolved price configuration.
	 */
	public abstract P getPrice();

	/**
	 * Set the resolved price configuration.
	 *
	 * @param price The resolved price.
	 */
	public abstract void setPrice(P price);

	/**
	 * Return the effective location applied to the current resource.
	 *
	 * @return The related location. Never <code>null</code>.
	 */
	@Transient
	@JsonIgnore
	public ProvLocation getResolvedLocation() {
		return location == null ? getConfiguration().getLocation() : location;
	}

	/**
	 * Return the resource type.
	 *
	 * @return The resource type.
	 */
	public abstract ResourceType getResourceType();

}
