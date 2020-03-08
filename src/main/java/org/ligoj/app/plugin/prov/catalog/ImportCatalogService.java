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
	 * @param node The node to update.
	 * @throws Exception When the catalog update fails. The error is caught at upper level.
	 */
	@CacheEvict(allEntries = true, cacheNames = { "prov-license", "prov-software", "prov-processor",
			"prov-instance-type", "prov-instance-type-dyn", "prov-instance-type-has-dyn", "prov-location", "prov-database-type",
			"prov-database-type-dyn", "prov-database-type-has-dyn", "prov-database-engine", "prov-database-edition", "prov-database-license",
			"prov-instance-term" })
	void updateCatalog(String node) throws Exception;
}
