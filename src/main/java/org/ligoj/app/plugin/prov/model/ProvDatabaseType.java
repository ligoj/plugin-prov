/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.Setter;

/**
 * A database instance with characteristics
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_DATABASE_TYPE", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "node" }),
		@UniqueConstraint(columnNames = { "code", "node" }) })
public class ProvDatabaseType extends AbstractInstanceType {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

}
