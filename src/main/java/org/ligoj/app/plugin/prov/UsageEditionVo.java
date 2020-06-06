/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.bootstrap.core.NamedBean;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Usage definition inside a quote.
 */
@Getter
@Setter
@Accessors(chain = true)
public class UsageEditionVo extends NamedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Usage rate base 100.
	 */
	@Positive
	@Max(100)
	@NotNull
	private Integer rate = 100;

	/**
	 * Usage duration in months.
	 */
	@Positive
	@Max(72)
	private int duration = 1;

	/**
	 * Start of the evaluation. Negative number is accepted and means a past start. <code>0</code> means an immediate
	 * start.
	 */
	@PositiveOrZero
	private int start = 0;

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
	private double initialCost = 0;

}
