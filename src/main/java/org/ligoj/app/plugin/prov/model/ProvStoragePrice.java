/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage price for a storage type.<br>
 * The meaning to the cost attribute is the monthly cost of 1GiB (1024 MiB).
 */
@Getter
@Entity
@Table(name = "LIGOJ_PROV_STORAGE_PRICE", uniqueConstraints = { @UniqueConstraint(columnNames = { "type", "location" }),
		@UniqueConstraint(columnNames = { "code" }) })
public class ProvStoragePrice extends AbstractPrice<ProvStorageType> {

	/**
	 * The monthly cost of 1GiB (Gibibyte Bytes).
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Gibibyte">Gibibyte</a>
	 */
	@Setter
	private double costGb = 0;

	/**
	 * The monthly CO2 consumption of 1GiB (Gibibyte Bytes).
	 */
	private double co2Gb = 0;

	/**
	 * The cost per million transactions. May be <code>0</code>.
	 */
	@Setter
	private double costTransaction;

	/**
	 * The co2 consumption per million transactions. May be <code>0</code>.
	 */
	private double co2Transaction;
}
