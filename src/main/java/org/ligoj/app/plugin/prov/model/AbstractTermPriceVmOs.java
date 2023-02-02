/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A priced instance based resource withOS and billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to allow
 * quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - (initialCost / 24 / 365)</code>.
 *
 * @param <T> Resource type.
 */
@Getter
@Setter
@ToString(of = { "os" }, callSuper = true)
@MappedSuperclass
public abstract class AbstractTermPriceVmOs<T extends ProvType> extends AbstractTermPriceVm<T> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The related OS.
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(length = 50)
	private VmOs os;

}
