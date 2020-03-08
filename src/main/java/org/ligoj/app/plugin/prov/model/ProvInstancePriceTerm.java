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

import org.ligoj.app.api.NodeScoped;
import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance price term configuration
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE_TERM", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvInstancePriceTerm extends AbstractDescribedEntity<Integer> implements NodeScoped {

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
	 * The related node (VM provider) of this instance.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;

	/**
	 * The price may vary within the period.
	 */
	private boolean variable;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

	/**
	 * The internal offer code.
	 */
	@NotNull
	private String code;

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

}
