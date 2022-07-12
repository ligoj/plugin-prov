/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
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
	 * Mapping from API region identifier to region definition.
	 */
	@Getter
	private Map<String, ProvLocation> mapRegionById = new HashMap<>();

	/**
	 * The previously installed instance types. Key is the instance code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvInstanceType> instanceTypes = new ConcurrentHashMap<>();

	/**
	 * The previously installed container types. Key is the container code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvContainerType> containerTypes = new ConcurrentHashMap<>();

	/**
	 * The previously installed function types. Key is the function code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvFunctionType> functionTypes = new ConcurrentHashMap<>();

	/**
	 * The previously installed support types. Key is the support name.
	 */
	@Getter
	@Setter
	private Map<String, ProvSupportType> supportTypes = new HashMap<>();

	/**
	 * The previously installed database types. Key is the database code.
	 */
	@Getter
	@Setter
	protected Map<String, ProvDatabaseType> databaseTypes = new ConcurrentHashMap<>();

	/**
	 * The previously installed price term's codes.
	 */
	@Getter
	@Setter
	protected Map<String, ProvInstancePriceTerm> priceTerms = new ConcurrentHashMap<>();

	/**
	 * The previous installed instance prices. Key is the code.
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
	 * The previous installed container prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvContainerPrice> previousContainer = new HashMap<>();

	/**
	 * The previous installed function prices. Key is the code.
	 */
	@Getter
	private Map<String, ProvFunctionPrice> previousFunction = new HashMap<>();

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
	private Map<String, ProvLocation> regions = Collections.synchronizedMap(new HashMap<>());

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
	private Map<String, ProvStorageType> storageTypes = new ConcurrentHashMap<>();

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
	 * Valid database type pattern.
	 */
	@Getter
	@Setter
	private Pattern validContainerType;

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
	 * Base URL of catalog.
	 */
	@Getter
	@Setter
	private String baseUrl;

	/**
	 * Hours per month.
	 */
	@Getter
	@Setter
	private double hoursMonth = ProvResource.DEFAULT_HOURS_MONTH;

	/**
	 * CO2 data set.
	 */
	@Getter
	@Setter
	private Map<String, Co2Data> co2DataSet = new HashMap<>();

	/**
	 * Ingored CO2 data set. Key is the instance type to ignore, either explicitly, either already warned.
	 */
	@Getter
	@Setter
	private Map<String, Boolean> co2DataSetIgnored = new ConcurrentHashMap<>();

	protected AbstractUpdateContext(AbstractUpdateContext parent) {
		this();
		setForce(parent.isForce());
		setHoursMonth(parent.getHoursMonth());
		setNode(parent.getNode());
		this.regions = parent.regions;
		this.baseUrl = parent.baseUrl;
		this.mergedTypes = parent.mergedTypes;
		this.mergedTerms = parent.mergedTerms;
		this.mergedLocations = parent.mergedLocations;
		this.storageTypes = parent.storageTypes;
		this.priceTerms = parent.priceTerms;
		this.validDatabaseEngine = parent.validDatabaseEngine;
		this.validDatabaseType = parent.validDatabaseType;
		this.validContainerType = parent.validContainerType;
		this.validInstanceType = parent.validInstanceType;
		this.validRegion = parent.validRegion;
		this.validOs = parent.validOs;
		this.mapRegionById = parent.getMapRegionById();
		this.co2DataSet = parent.getCo2DataSet();
		this.co2DataSetIgnored = parent.getCo2DataSetIgnored();
	}

	/**
	 * Return amount of seconds in a standard month according to {@link #hoursMonth} configuration.
	 * 
	 * @return Amount of seconds in a standard month according to {@link #hoursMonth} configuration.
	 */
	public double getSecondsMonth() {
		return getHoursMonth() * DateUtils.MILLIS_PER_HOUR / DateUtils.MILLIS_PER_SECOND;
	}

	public void setPrevious(final Map<String, ProvInstancePrice> previous) {
		this.previous = previous;
		this.previousDatabase.clear();
		this.previousContainer.clear();
		this.previousFunction.clear();
		this.prices.clear();
	}

	public void setPreviousDatabase(final Map<String, ProvDatabasePrice> previous) {
		this.previous.clear();
		this.previousDatabase = previous;
		this.previousContainer.clear();
		this.previousFunction.clear();
		this.prices.clear();
	}

	public void setPreviousContainer(final Map<String, ProvContainerPrice> previous) {
		this.previousContainer = previous;
		this.previousDatabase.clear();
		this.previousFunction.clear();
		this.previous.clear();
		this.prices.clear();
	}

	public void setPreviousFunction(final Map<String, ProvFunctionPrice> previous) {
		this.previousFunction = previous;
		this.previousContainer.clear();
		this.previousDatabase.clear();
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
		this.containerTypes.clear();
		this.functionTypes.clear();
		this.instanceTypes.clear();
		this.previousStorage.clear();
		this.previousDatabase.clear();
		this.previousContainer.clear();
		this.previousFunction.clear();
		this.previousSupport.clear();
		this.priceTerms.clear();
	}

	/**
	 * Return the full URL based on the base URL of this context.
	 * 
	 * @param relative URL. <code>/</code> is prepended if missing.
	 * @return The full URL based on the base URL of this context.
	 */
	public String getUrl(final String relative) {
		return baseUrl + StringUtils.prependIfMissing(relative, "/");
	}
}
