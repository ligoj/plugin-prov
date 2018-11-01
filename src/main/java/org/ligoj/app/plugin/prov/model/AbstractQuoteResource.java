/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
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
 * @param <T>
 *            Price configuration type.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuoteResource<P extends AbstractPrice<?>> extends AbstractDescribedEntity<Integer>
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
	 * @Return Resolved price configuration.
	 */
	public abstract P getPrice();

}
