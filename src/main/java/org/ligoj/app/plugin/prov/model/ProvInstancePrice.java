/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An priced instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to be
 * allow quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@ToString(of = { "os", "term", "tenancy", "license" }, callSuper = true)
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "location", "type", "os", "term", "tenancy", "license" }),
		@UniqueConstraint(columnNames = "code") })
public class ProvInstancePrice extends AbstractPrice<ProvInstanceType> implements Serializable {

	/**
	 * Bring your own license.
	 */
	public static final String LICENSE_BYOL = "BYOL";

	/**
	 * The optional initial cost required to use this instance. May be <code>null</code>.
	 */
	private Double initialCost;

	/**
	 * The optional monthly cost of one requested CPU. May be <code>null</code>.
	 */
	private Double costCpu;

	/**
	 * The optional monthly cost of one requested GB memory. May be <code>null</code>.
	 */
	private Double costRam;

	/**
	 * The cost for the period.<br>
	 */
	private double costPeriod;

	@NotNull
	@ManyToOne
	private ProvInstancePriceTerm term;

	/**
	 * The optional tenancy of the related instance. By default, the tenancy is {@link ProvTenancy#SHARED}
	 */
	@Enumerated(EnumType.STRING)
	private ProvTenancy tenancy = ProvTenancy.SHARED;

	/**
	 * The related price
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	private VmOs os;

	/**
	 * Optional built-in license model. Special license term is {@value #LICENSE_BYOL}. When <code>null</code>, license
	 * is included in the price or not applicable.
	 */
	private String license;

	/**
	 * Optional built-in software.
	 */
	private String software;
}
