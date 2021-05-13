/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import org.springframework.cache.annotation.CacheEvict;

/**
 * Provisioning contract with updated catalog.
 */
public interface ImportCatalogService {

	/**
	 * Update the catalog.
	 *
	 * @param node  The node to update.
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @throws Exception When the catalog update fails. The error is caught at upper level.
	 */
	@CacheEvict(allEntries = true, cacheNames = { "prov-instance-license", "prov-container-license",
			"prov-instance-software", "prov-instance-os", "prov-container-os", "prov-processor", "prov-instance-type",
			"prov-instance-type-dyn", "prov-instance-type-has-dyn", "prov-container-type", "prov-container-type-dyn",
			"prov-container-type-has-dyn", "prov-location", "prov-database-type", "prov-database-type-dyn",
			"prov-database-type-has-dyn", "prov-database-engine", "prov-database-edition", "prov-database-license",
			"prov-instance-term" })
	void updateCatalog(String node, boolean force) throws Exception;
}
