/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.List;
import java.util.Map;

import org.ligoj.app.plugin.prov.model.ProvLocation;

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
	 * All available locations of this provider, ordered by name. Full objects (country, coordinates, ...) so the UI
	 * can render the flag and the localized country name.
	 */
	private List<ProvLocation> locations;

	/**
	 * The requested provider scoped configuration values. Key is the full configuration name. Only the defined
	 * configurations are present.
	 */
	private Map<String, String> properties;
}
