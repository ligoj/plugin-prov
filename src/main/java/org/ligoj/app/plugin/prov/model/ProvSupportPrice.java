/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.PositiveOrZero;

import lombok.Getter;
import lombok.Setter;

/**
 * Support price for a support type.<br>
 * The meaning to the cost attribute is the fixed monthly cost for all provided seats. May be zero.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_SUPPORT_PRICE", uniqueConstraints = { @UniqueConstraint(columnNames = { "location", "type" }),
		@UniqueConstraint(columnNames = "code") })
public class ProvSupportPrice extends AbstractPrice<ProvSupportType> implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Minimal support pricing.
	 */
	@PositiveOrZero
	private int min;

	/**
	 * Base 100 percentages. Each rate use ';' as separator, and its order must correspond to the {@link #limit}'s one.
	 * May be <code>null</code> or empty.
	 */
	private String rate;

	/**
	 * Maximal price limit where corresponding {@link #rate} is applicable. Each limit use ';' as separator, and its.
	 * May be <code>null</code> or empty. order must correspond to the {@link #rate}'s one.
	 */
	private String limit;

}
