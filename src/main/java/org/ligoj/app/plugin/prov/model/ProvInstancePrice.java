package org.ligoj.app.plugin.prov.model;

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
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE", uniqueConstraints = @UniqueConstraint(columnNames = { "type", "os", "term",
		"tenancy", "license", "location" }))
public class ProvInstancePrice extends AbstractPrice<ProvInstanceType> {

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
	 * Optional license model.
	 */
	private String license;
}
