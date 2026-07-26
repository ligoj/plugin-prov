/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

/**
 * Provisioning contract with updated catalog.
 */
public interface ImportCatalogService {

	/**
	 * The catalog-derived caches to evict after a catalog update, so the new engines, types, terms, ... are visible
	 * without delay. The eviction is performed programmatically by the catalog runner: a <code>@CacheEvict</code>
	 * annotation on this interface method would be ignored by the class-based (CGLIB) proxies of the implementors.
	 */
	String[] EVICTED_CACHES = { "prov-instance-license", "prov-container-license",
			"prov-instance-software", "prov-instance-os", "prov-container-os", "prov-processor", "prov-instance-type",
			"prov-instance-type-dyn", "prov-instance-type-has-dyn", "prov-container-type", "prov-container-type-dyn",
			"prov-container-type-has-dyn", "prov-location", "prov-database-type", "prov-database-type-dyn",
			"prov-database-type-has-dyn", "prov-database-engine", "prov-database-edition", "prov-database-license",
			"prov-instance-term", "prov-function-type", "prov-function-type-dyn", "prov-function-type-has-dyn",
			"prov-instance-has-co2", "prov-function-has-co2", "prov-container-has-co2", "prov-database-has-co2",
			"prov-architecture" };

	/**
	 * Update the catalog.
	 *
	 * @param node  The node to update.
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @throws Exception When the catalog update fails. The error is caught at upper level.
	 */
	void updateCatalog(String node, boolean force) throws Exception;
}
