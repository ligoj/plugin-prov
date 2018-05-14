/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
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
	 * @throws Exception
	 *             When the catalog update fails. The error is caught at upper
	 *             level.
	 */
	void updateCatalog(String node) throws Exception;
}
