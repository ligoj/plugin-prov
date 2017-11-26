package org.ligoj.app.plugin.prov.model;

import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;
import org.ligoj.bootstrap.core.model.ToNameSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource with floating cost.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractQuoteResource extends AbstractDescribedEntity<Integer> implements Costed {

	/**
	 * The minimal computed monthly cost of the resource.
	 */
	@NotNull
	@PositiveOrZero
	private Double cost;

	/**
	 * Maximal determined monthly cost of the resource. When there is an unbound
	 * maximal (<code>null</code>) quantity, the minimal cost is used.
	 */
	@NotNull
	@PositiveOrZero
	private Double maxCost;

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
	@JsonSerialize(using = ToNameSerializer.class)
	private ProvLocation location;
}
