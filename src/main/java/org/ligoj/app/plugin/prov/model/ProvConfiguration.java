/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.ligoj.bootstrap.core.model.AbstractBusinessEntity;

/**
 * Configuration of a provider. The key is the provider node's identifier. One configuration per node.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_CONFIGURATION")
public class ProvConfiguration extends AbstractBusinessEntity<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The default location name used by the new quotes of this provider. This is a location name, not a foreign key: a
	 * catalog update deleting this location must not delete this configuration.
	 */
	private String defaultLocation;
}
