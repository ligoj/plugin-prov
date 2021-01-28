/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An priced instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@ToString(of = { "tenancy" }, callSuper = true)
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "location", "os", "tenancy", "license", "software", "term", "type" }),
		@UniqueConstraint(columnNames = "code") })
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
	private ProvTenancy tenancy = ProvTenancy.SHARED;

	/**
	 * Optional built-in software.
	 */
	private String software;
}
