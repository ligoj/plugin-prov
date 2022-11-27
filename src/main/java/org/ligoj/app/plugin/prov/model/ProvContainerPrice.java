/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * A priced container with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this container. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_CONTAINER_PRICE", uniqueConstraints = { @UniqueConstraint(columnNames = "code") }, indexes = {
		@Index(name = "lookup_c_index", columnList = "location,type,term,os,increment_cpu,license") })
public class ProvContainerPrice extends AbstractTermPriceVmOs<ProvContainerType> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

}
