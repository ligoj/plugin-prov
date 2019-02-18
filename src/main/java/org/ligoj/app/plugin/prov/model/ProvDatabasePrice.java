/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * An priced database instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to be
 * allow quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@ToString(of = { "engine", "edition" }, callSuper = true)
@Table(name = "LIGOJ_PROV_DATABASE_PRICE", uniqueConstraints = {
		@UniqueConstraint(columnNames = { "location", "engine", "edition", "license", "term", "type" }),
		@UniqueConstraint(columnNames = "code") })
public class ProvDatabasePrice extends AbstractTermPrice<ProvDatabaseType> {

	/**
	 * Database engine. Like: "Oracle", "MySQL",...
	 */
	@NotNull
	private String engine;

	/**
	 * Optional database edition. Like: "Standard", "Standard One", "Enterprise"
	 */
	private String edition;

	/**
	 * Required storage engine. When not <code>null</code>, this type requires a storage only compatible to this engine.
	 * When <code>null</code>, this type requires a storage having no engine constraint.
	 *
	 * @see {@link ProvStorageType#getEngine()}
	 */
	private String storageEngine;
}
