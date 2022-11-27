/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A priced instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@ToString(of = { "tenancy" }, callSuper = true)
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE", uniqueConstraints = { @UniqueConstraint(columnNames = "code") }, indexes = {
		@Index(name = "lookup_index", columnList = "location,type,term,os,tenancy,increment_cpu,license,software") })
public class ProvInstancePrice extends AbstractTermPriceVmOs<ProvInstanceType> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Bring your own license.
	 */
	public static final String LICENSE_BYOL = "BYOL";

	/**
	 * The optional tenancy of the related instance. By default, the tenancy is {@link ProvTenancy#SHARED}
	 */
	@Enumerated(EnumType.STRING)
	@Column(length = 50)
	private ProvTenancy tenancy = ProvTenancy.SHARED;

	/**
	 * Optional built-in software.
	 */
	@Column(length = 100)
	private String software;
}
