/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.ligoj.app.model.AbstractLongTaskNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Import catalog status. Only one import per provider at the same moment.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_IMPORT_CATALOG_STATUS", uniqueConstraints = @UniqueConstraint(columnNames = "locked"))
public class ImportCatalogStatus extends AbstractLongTaskNode {

	/**
	 * The last time this catalog was updated. Can be <code>null</code>.
	 */
	private Date lastSuccess;

	/**
	 * Number of imported locations from the last successful import.
	 */
	private Integer nbLocations = 0;

	/**
	 * Number of imported instance types from the last successful import.
	 */
	private Integer nbTypes = 0;

	/**
	 * Number of imported instance prices from the last successful import.
	 */
	private Integer nbPrices = 0;

	/**
	 * Number of imported instance prices from the last successful import having CO2 data.
	 */
	private Integer nbCo2Prices = 0;

	/**
	 * The current region being imported.
	 */
	private String location;

	/**
	 * The current phase : instance, storage,...
	 */
	private String phase;

	/**
	 * The current step : includes regions and other tasks. Minimum 0, maximum equals to workload.
	 */
	private int done;

	/**
	 * The total work to do : includes regions and other tasks.
	 */
	private int workload;

}
