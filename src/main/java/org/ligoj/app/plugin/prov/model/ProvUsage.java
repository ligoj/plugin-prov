/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Named resource usage : 1 to 100. Corresponds to percentage.
 */
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "LIGOJ_PROV_USAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "configuration" }))
public class ProvUsage extends AbstractNamedEntity<Integer> implements Configurable<ProvQuote, Integer> {

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
	 * Usage rate base 100.
	 */
	@Positive
	@Max(100)
	@NotNull
	private Integer rate = 100;

	/**
	 * Duration of this usage in month.
	 */
	@Positive
	private int duration = 1;

	/**
	 * Start of the evaluation. Negative number is accepted and means a past start. <code>null</code> or zero means an
	 * immediate start.
	 */
	private Integer start = 0;

	/**
	 * When <code>true</code>, the resolved OS may be changed during the commitment, otherwise is <code>false</code>.
	 */
	private Boolean convertibleOs;

	/**
	 * When <code>true</code>, the resolved engine may be changed during the commitment, otherwise is
	 * <code>false</code>.
	 */
	private Boolean convertibleEngine;

	/**
	 * When <code>true</code>, the resolved location may be changed during the commitment, otherwise is
	 * <code>false</code>.
	 */
	private Boolean convertibleLocation;

	/**
	 * When <code>true</code>, the resolved family may be changed during the commitment, otherwise is
	 * <code>false</code>.
	 */
	private Boolean convertibleFamily;

	/**
	 * When <code>true</code>, the resolved type may be changed during the commitment, otherwise is <code>false</code>.
	 */
	private Boolean convertibleType;

	/**
	 * When <code>true</code>, a reservation is required, otherwise is <code>false</code>.
	 */
	private Boolean reservation;

	/**
	 * The maximal accepted initial cost. When <code>null</code>, is <code>0</code>.
	 */
	@PositiveOrZero
	private Double initialCost;

}
