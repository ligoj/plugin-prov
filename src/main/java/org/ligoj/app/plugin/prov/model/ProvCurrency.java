/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.ligoj.bootstrap.core.model.AbstractDescribedAuditedEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A currency configuration modifying the cost.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_CURRENCY", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class ProvCurrency extends AbstractDescribedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The rate to apply to the cost.
	 */
	@NotNull
	@Positive
	private double rate = 1d;

	/**
	 * The currency unit displayed around the amount.
	 */
	@NotBlank
	private String unit;

}
