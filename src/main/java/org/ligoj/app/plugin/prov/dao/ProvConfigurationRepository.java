/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.ligoj.app.plugin.prov.model.ProvConfiguration;
import org.ligoj.bootstrap.core.dao.RestRepository;

/**
 * {@link ProvConfiguration} repository. The key is the provider node's identifier.
 */
public interface ProvConfigurationRepository extends RestRepository<ProvConfiguration, String> {

	// All delegated
}
