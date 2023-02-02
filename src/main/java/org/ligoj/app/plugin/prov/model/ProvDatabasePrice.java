/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * A priced database instance with billing configuration. <br>
 * The cost attribute is the corresponding effective monthly cost of this instance. Includes the initial cost to
 * allow quick sort. To compute the remaining monthly cost reduced by the initial cost, the formula is :
 * <code>cost - initialCost / 24 / 365</code>.
 */
@Getter
@Setter
@Entity
@ToString(of = { "engine", "edition" }, callSuper = true)
@Table(name = "LIGOJ_PROV_DATABASE_PRICE", uniqueConstraints = { @UniqueConstraint(columnNames = "code") }, indexes = {
		@Index(name = "lookup_d_index", columnList = "location,type,term,engine,edition,increment_cpu,license") })
public class ProvDatabasePrice extends AbstractTermPriceVm<ProvDatabaseType> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

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
	 * When <code>null</code>, this type requires a storage having no engine constraint. Storage compatibility is also
	 * implied from {@link org.ligoj.app.plugin.prov.model.ProvStorageType#getEngine()}.
	 */
	private String storageEngine;
}
