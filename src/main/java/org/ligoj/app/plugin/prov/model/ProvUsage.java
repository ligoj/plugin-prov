/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
public class ProvUsage extends AbstractMultiScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Default usage rate.
	 */
	public static final int MAX_RATE = 100;

	/**
	 * Usage rate base 100.
	 */
	@Positive
	@Max(MAX_RATE)
	@NotNull
	private Integer rate = MAX_RATE;

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

}
