package org.ligoj.app.plugin.prov.in;

/**
 * Provisioning contract with updated catalog.
 */
public interface ImportCatalogService {

	/**
	 * Update the catalog.
	 * 
	 * @param node
	 *            The node to update.
	 */
	void updateCatalog(String node) throws Exception;
}
