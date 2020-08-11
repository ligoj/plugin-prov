/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.Collections;
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
	protected Node node;

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
	private final Map<String, ProvLocation> mapRegionToName = new HashMap<>();

	/**
	 * The previously installed instance types. Key is the instance code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvInstanceType> instanceTypes = new HashMap<>();

	/**
	 * The previously installed support types. Key is the instance name.
	 */
	@Getter
	@Setter
	private Map<String, ProvSupportType> supportTypes = new HashMap<>();

	/**
	 * The previously installed database types. Key is the database code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvDatabaseType> databaseTypes = new HashMap<>();

	/**
	 * The previously installed price term's codes.
	 */
	@Getter
	@Setter
	protected Map<String, ProvInstancePriceTerm> priceTerms = new HashMap<>();

	/**
	 * The previous installed EC2 prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvInstancePrice> previous = new HashMap<>();

	/**
	 * The read catalog price codes: codes having been read from the catalog and persisted.
	 */
	@Getter
	private final Set<String> prices = new HashSet<>();

	/**
	 * The previous installed Database prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvDatabasePrice> previousDatabase = new HashMap<>();

	/**
	 * The previous installed storage prices. Key is the code.
	 */
	@Getter
	@Setter
	private Map<String, ProvStoragePrice> previousStorage = new HashMap<>();

	/**
	 * The previous installed support prices. Key is the name.
	 */
	@Getter
	@Setter
	private Map<String, ProvSupportPrice> previousSupport = new HashMap<>();

	/**
	 * The available regions. Key is the name.
	 */
	@Getter
	@Setter
	private Map<String, ProvLocation> regions = new HashMap<>();

	/**
	 * The merged type's codes.
	 */
	@Getter
	private Set<String> mergedTypes = Collections.synchronizedSet(new HashSet<>());

	/**
	 * The merged term's codes.
	 */
	@Getter
	private Set<String> mergedTerms = Collections.synchronizedSet(new HashSet<>());

	/**
	 * The merged location's codes.
	 */
	@Getter
	private Set<String> mergedLocations = Collections.synchronizedSet(new HashSet<>());

	/**
	 * The accepted and existing storage type. Key is the code.
	 */
	@Getter
	@Setter
	private Map<String, ProvStorageType> storageTypes = new HashMap<>();

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

	/**
	 * Hours per month.
	 */
	@Getter
	@Setter
	private double hoursMonth = AbstractImportCatalogResource.DEFAULT_HOURS_MONTH;

	protected AbstractUpdateContext(AbstractUpdateContext parent) {
		this();
		setForce(parent.isForce());
		setHoursMonth(parent.getHoursMonth());
		setNode(parent.getNode());
		this.mergedTypes = parent.mergedTypes;
		this.mergedTerms = parent.mergedTerms;
		this.mergedLocations = parent.mergedLocations;
		this.storageTypes = parent.storageTypes;
		this.priceTerms = parent.priceTerms;
		this.validDatabaseEngine = parent.validDatabaseEngine;
		this.validDatabaseType = parent.validDatabaseType;
		this.validInstanceType = parent.validInstanceType;
		this.validRegion = parent.validRegion;
		this.validOs = parent.validOs;
	}

	public void setPrevious(final Map<String, ProvInstancePrice> previous) {
		this.previous = previous;
		this.previousDatabase.clear();
		this.prices.clear();
	}

	public void setPreviousDatabase(final Map<String, ProvDatabasePrice> previous) {
		this.previousDatabase = previous;
		this.previous.clear();
		this.prices.clear();
	}

	/**
	 * Release pointers.
	 */
	public void cleanup() {
		this.prices.clear();
		this.mergedTypes.clear();
		this.mergedTerms.clear();
		this.mergedLocations.clear();
		this.storageTypes.clear();
		this.supportTypes.clear();
		this.storageTypes.clear();
		this.previous.clear();
		this.databaseTypes.clear();
		this.instanceTypes.clear();
		this.previousStorage.clear();
		this.previousDatabase.clear();
		this.previousSupport.clear();
		this.priceTerms.clear();
		this.regions.clear();
	}
}
