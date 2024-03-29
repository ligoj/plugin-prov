/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage specification of a provider. <br>
 * Sizes use the GiB unit.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Gibibyte">https://en.wikipedia.org/wiki/Gibibyte</a>
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_STORAGE_TYPE", uniqueConstraints = { @UniqueConstraint(columnNames = { "name", "node" }),
		@UniqueConstraint(columnNames = { "code", "node" }) })
public class ProvStorageType extends AbstractCodedEntity {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The latency class.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate latency;

	/**
	 * Optimized usage of this storage
	 */
	@Enumerated(EnumType.STRING)
	private ProvStorageOptimized optimized;

	/**
	 * The minimal disk size in "GiB".
	 */
	private double minimal = 1;

	/**
	 * The maximum supported size in "GiB". May be <code>null</code> for unlimited size.
	 */
	private Double maximal;

	/**
	 * Optional increment size in "GiB". Default is <code>1</code>.
	 */
	private Double increment;

	/**
	 * Maximum IOPS. When <code>null</code> or <code>0</code>, is undefined.
	 */
	private int iops;

	/**
	 * Maximum throughput in MB/s. When <code>null</code> or <code>0</code>, is undefined.
	 */
	private int throughput;

	/**
	 * When not <code>null</code>, this storage can only be attached to an instance whose type's code is matching the
	 * expression.
	 */
	private String instanceType = null;

	/**
	 * When not <code>null</code>, this storage can not be attached to an instance whose type's code is matching the
	 * expression.
	 */
	private String notInstanceType = null;
	/**
	 * When not <code>null</code>, this storage can only be attached to a container whose type's code is matching the
	 * expression.
	 */
	private String containerType = null;

	/**
	 * When not <code>null</code>, this storage can not be attached to a container whose type's code is matching the
	 * expression.
	 */
	private String notContainerType = null;
	/**
	 * When not <code>null</code>, this storage can only be attached to a function whose type's code is matching the
	 * expression.
	 */
	private String functionType = null;

	/**
	 * When not <code>null</code>, this storage can not be attached to a function whose type's code is matching the
	 * expression.
	 */
	private String notFunctionType = null;

	/**
	 * When not <code>null</code>, this storage can only be attached to a database whose type is matching the expression.
	 */
	private String databaseType = null;

	/**
	 * When not <code>null</code>, this storage cannot be attached to a database whose type is matching the expression.
	 */
	private String notDatabaseType = null;

	/**
	 * When not <code>null</code>, this storage can only be attached to a database type providing this engine. When
	 * <code>null</code>, this storage can only be attached to a database type not requiring a specific storage engine.
	 * Compatibility is also implied from {@link org.ligoj.app.plugin.prov.model.ProvDatabasePrice#getStorageEngine()}
	 * for sample.
	 */
	private String engine;

	/**
	 * Optional advertised availability. When <code>null</code>, the availability is not provided.
	 */
	private Double availability;

	/**
	 * Optional advertised durability with "9" digits:
	 * <ul>
	 * <li><code>1</code> means <code>90%</code></li>
	 * <li><code>2</code> means <code>99%</code></li>
	 * <li><code>3</code> means <code>99.9%</code></li>
	 * <li><code>N</code> means <code>99.9{repeated N-2}%</code></li>
	 * </ul>
	 * When <code>null</code>, the durability is not provided.
	 */
	@Positive
	private Integer durability9;

	/**
	 * When not <code>null</code>, this storage is reachable using network using the given port by default. The string
	 * is a network specification: <code>tcp</code>, <code>nfs</code>, ...
	 */
	private String network;

}
