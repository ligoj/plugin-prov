/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provider configuration edition: default location and provider scoped configuration properties.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEditionVo {

	@NotNull
	private String node;

	/**
	 * The default location name for the new quotes. May be <code>null</code>.
	 */
	private String defaultLocation;

	/**
	 * The provider scoped configuration properties to update. Key is the full configuration name, prefixed by the
	 * provider node's identifier. A <code>null</code> or empty value deletes the configuration entry. May be
	 * <code>null</code>.
	 */
	private Map<String, String> properties;
}
