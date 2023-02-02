/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.PositiveOrZero;

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
public class ProvBudget extends AbstractMultiScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

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
	@Transient
	@JsonIgnore
	private Double remainingBudget;
}
