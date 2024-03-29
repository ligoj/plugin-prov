/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import org.ligoj.app.api.NodeVo;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvLocation;

import lombok.Getter;
import lombok.Setter;

/**
 * The catalog with optional status
 */
@Getter
@Setter
public class CatalogVo {

	/**
	 * Optional status.
	 */
	private ImportCatalogStatus status;

	/**
	 * The node
	 */
	private NodeVo node;

	/**
	 * When <code>true</code>, this provider support remote import.
	 */
	private boolean canImport;

	/**
	 * The amount of quotes using this catalog.
	 */
	private int nbQuotes;

	/**
	 * The preferred location, may be <code>null</code>.
	 */
	private ProvLocation preferredLocation;

}
