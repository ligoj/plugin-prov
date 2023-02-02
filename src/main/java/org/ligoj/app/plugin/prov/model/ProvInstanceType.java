/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance with characteristics
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_INSTANCE_TYPE", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "node" }),
		@UniqueConstraint(columnNames = { "code", "node" }) })
public class ProvInstanceType extends AbstractInstanceType {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

}
