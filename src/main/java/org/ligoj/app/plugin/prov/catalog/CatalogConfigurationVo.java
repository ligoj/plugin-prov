/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * The current provider configuration: default location, available locations and the requested provider scoped
 * configuration values.
 */
@Getter
@Setter
public class CatalogConfigurationVo {

	/**
	 * The default location name for the new quotes. May be <code>null</code>.
	 */
	private String defaultLocation;

	/**
	 * All available location names of this provider, ordered by name.
	 */
	private List<String> locations;

	/**
	 * The requested provider scoped configuration values. Key is the full configuration name. Only the defined
	 * configurations are present.
	 */
	private Map<String, String> properties;
}
