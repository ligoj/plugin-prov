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

	public CurrencyVo(ProvCurrency parent, long nbQuotes) {
		this.nbQuotes = nbQuotes;
		setId(parent.getId());
		setUnit(parent.getUnit());
		setName(parent.getName());
		setDescription(parent.getDescription());
		setRate(parent.getRate());
	}

	/**
	 * Amount of quotes using this currency.
	 */
	private long nbQuotes;
}
