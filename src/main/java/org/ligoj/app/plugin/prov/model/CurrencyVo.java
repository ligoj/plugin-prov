/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import lombok.Getter;

/**
 * A currency view.
 */
@Getter
public class CurrencyVo extends ProvCurrency {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Amount of quotes using this currency.
	 */
	private final long nbQuotes;

	/**
	 * Create a simple currency view from a given JPA entity and related statistics.
	 *
	 * @param parent   The {@link ProvCurrency} entity.
	 * @param nbQuotes The amount of quotes using this currency.
	 */
	public CurrencyVo(final ProvCurrency parent, final long nbQuotes) {
		this.nbQuotes = nbQuotes;
		setId(parent.getId());
		setUnit(parent.getUnit());
		setName(parent.getName());
		setDescription(parent.getDescription());
		setRate(parent.getRate());
	}
}
