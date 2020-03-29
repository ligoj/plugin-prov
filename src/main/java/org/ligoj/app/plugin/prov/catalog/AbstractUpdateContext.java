/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Base context used to perform catalog update.
 */
@NoArgsConstructor
public abstract class AbstractUpdateContext {
	/**
	 * The related AWS {@link Node}
	 */
	@Getter
	@Setter
	private Node node;

	/**
	 * When <code>true</code>, all cost attributes are update.
	 */
	@Getter
	@Setter
	private boolean force;

	/**
	 * Mapping from API region identifier to region name.
	 */
	@Getter
	private Map<String, ProvLocation> mapRegionToName = new HashMap<>();

	/**
	 * The previously installed instance types. Key is the instance code.
	 */
	@Getter
	@Setter
	private Map<String, ProvInstanceType> instanceTypes;

	/**
	 * The previously installed support types. Key is the instance name.
	 */
	@Getter
	@Setter
	private Map<String, ProvSupportType> supportTypes;

	/**
	 * The previously installed database types. Key is the database code.
	 */
	@Getter
	@Setter
	private Map<String, ProvDatabaseType> databaseTypes;

	/**
	 * The previously installed price term's codes.
	 */
	@Getter
	@Setter
	private Map<String, ProvInstancePriceTerm> priceTerms;

	/**
	 * The previous installed EC2 prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvInstancePrice> previous;

	/**
	 * The updated/created catalog price codes.
	 */
	@Getter
	private Set<String> updatedPrices = new HashSet<String>();

	/**
	 * The previous installed Database prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvDatabasePrice> previousDatabase;

	/**
	 * The previous installed storage prices. Key is the code.
	 */
	@Getter
	@Setter
	private Map<String, ProvStoragePrice> previousStorage;

	/**
	 * The previous installed support prices. Key is the name.
	 */
	@Getter
	@Setter
	private Map<String, ProvSupportPrice> previousSupport;

	/**
	 * The available regions. Key is the name.
	 */
	@Getter
	@Setter
	private Map<String, ProvLocation> regions;

	/**
	 * The merged type's codes.
	 */
	@Getter
	private Set<String> mergedTypes = new HashSet<String>();

	/**
	 * The accepted and existing storage type. Key is the code.
	 */
	@Getter
	@Setter
	private Map<String, ProvStorageType> storageTypes;

	/**
	 * Valid OS pattern.
	 */
	@Getter
	@Setter
	private Pattern validOs;

	/**
	 * Valid instance type pattern.
	 */
	@Getter
	@Setter
	private Pattern validInstanceType;

	/**
	 * Valid database type pattern.
	 */
	@Getter
	@Setter
	private Pattern validDatabaseType;

	/**
	 * Valid database engine pattern.
	 */
	@Getter
	@Setter
	private Pattern validDatabaseEngine;

	/**
	 * Valid instance region pattern.
	 */
	@Getter
	@Setter
	private Pattern validRegion;

	private AbstractUpdateContext parent;

	/**
	 * Hours per month.
	 */
	@Getter
	@Setter
	private double hoursMonth = AbstractImportCatalogResource.DEFAULT_HOURS_MONTH;

	public AbstractUpdateContext(AbstractUpdateContext parent) {
		this();
		this.parent = parent;
		setForce(parent.isForce());
		setHoursMonth(parent.getHoursMonth());
		setNode(parent.getNode());
	}

	public void setPrevious(final Map<String, ProvInstancePrice> previous) {
		this.previous = previous;
		this.updatedPrices.clear();
		if (parent != null) {
			parent.updatedPrices = updatedPrices;
			parent.previous = previous;
		}
	}

	public void setPreviousDatabase(final Map<String, ProvDatabasePrice> previous) {
		this.previousDatabase = previous;
		this.updatedPrices.clear();
		if (parent != null) {
			parent.updatedPrices = updatedPrices;
			parent.previousDatabase = previousDatabase;
		}
	}
}
