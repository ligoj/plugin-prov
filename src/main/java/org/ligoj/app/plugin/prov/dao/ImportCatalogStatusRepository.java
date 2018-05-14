/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.dao.task.LongTaskNodeRepository;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;

/**
 * {@link ImportCatalogStatus} repository.
 */
public interface ImportCatalogStatusRepository extends LongTaskNodeRepository<ImportCatalogStatus> {

	// All is delegated
}
