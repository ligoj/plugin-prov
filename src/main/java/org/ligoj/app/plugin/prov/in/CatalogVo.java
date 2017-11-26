package org.ligoj.app.plugin.prov.in;

import org.ligoj.app.api.NodeVo;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * The catalog with optional status
 */
@Getter
@AllArgsConstructor
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
}
