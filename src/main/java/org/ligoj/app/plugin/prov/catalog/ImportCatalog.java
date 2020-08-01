/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Catalog resource import.
 *
 * @param <U>
 *            Context type.
 */
public interface ImportCatalog<U extends AbstractUpdateContext> {

	/**
	 * Install or update prices.
	 *
	 * @param context
	 *            The update context.
	 * @throws IOException
	 *             When CSV or XML files cannot be read.
	 * @throws URISyntaxException
	 *             When CSV or XML files cannot be read.
	 */
	void install(final U context) throws IOException;

}
