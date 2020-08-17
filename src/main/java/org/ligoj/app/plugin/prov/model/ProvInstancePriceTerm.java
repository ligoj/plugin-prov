/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.ligoj.app.api.NodeScoped;
import org.ligoj.bootstrap.core.model.ToNameSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance price term configuration
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE_TERM", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "name", "node" }), @UniqueConstraint(columnNames = { "code", "node" }) })
public class ProvInstancePriceTerm extends AbstractCodedEntity implements NodeScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Billing period duration in month. Any started period is due. When <code>0</code>, this assumes there is a billing
	 * period below 1 month.
	 */
	private double period = 0;

	/**
	 * The price may vary within the period.
	 */
	private boolean variable;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

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
	 * When <code>true</code>, a reservation is required, otherwise is <code>false</code>, no capacity reservation.
	 */
	private Boolean reservation;

	/**
	 * When <code>true</code>, this term is associated to initial cost.
	 */
	private Boolean initialCost = false;

	/**
	 * Optional location constraint. When not <code>null</code>, is redundant of the
	 * {@link org.ligoj.app.plugin.prov.model.AbstractPrice} <code>location</code>.
	 */
	@ManyToOne
	@JsonSerialize(using = ToNameSerializer.class)
	private ProvLocation location;

}
