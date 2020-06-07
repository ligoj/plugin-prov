/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Budget profile
 */
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "LIGOJ_PROV_BUDGET", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "configuration" }))
public class ProvBudget extends AbstractNamedEntity<Integer> implements Configurable<ProvQuote, Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The related quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

	/**
	 * The maximal accepted initial cost.
	 */
	@PositiveOrZero
	private double initialCost = 0;

	/**
	 * The maximal required initial cost. Cannot be greater than {@link #initialCost}.
	 */
	@PositiveOrZero
	@JsonProperty(access = Access.READ_ONLY)
	private double requiredInitialCost = 0;
	
	/**
	 * Remaining initial budget in the current transaction.
	 */
	@javax.persistence.Transient
	@JsonIgnore
	private Double remainingBudget;
}
