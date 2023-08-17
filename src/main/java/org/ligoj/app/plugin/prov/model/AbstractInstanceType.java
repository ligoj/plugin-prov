/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance with characteristics
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractInstanceType extends AbstractCodedEntity {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Amount of CPU. <code>0</code> for custom instance type.
	 */
	private double cpu;

	/**
	 * Amount of GPU. <code>0</code> for custom instance type.
	 */
	private double gpu;

	/**
	 * RAM (Memory) in "MiB" = "Mega Bytes".
	 */
	private double ram;

	/**
	 * Maximal efficient baseline CPU workload, from 0 to 100. Default is <code>100</code> for <code>null</code> value.
	 */
	private double baseline = 0;

	/**
	 * Optional physical processor. May be <code>null</code>.
	 */
	private String processor;

		/**
	 * Optional processor architecture. May be <code>null</code>.
	 */
	private String architecture;

	/**
	 * When <code>true</code>, this instance type is physical, not virtual.
	 */
	private Boolean physical = false;

	/**
	 * Edge capability.
	 */
	private Boolean edge = false;

	/**
	 * CPU performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate cpuRate = Rate.MEDIUM;

	/**
	 * GPU performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate gpuRate = Rate.MEDIUM;

	/**
	 * RAM performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate ramRate = Rate.MEDIUM;

	/**
	 * Storage performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate storageRate = Rate.MEDIUM;

	/**
	 * Network performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate networkRate = Rate.MEDIUM;

	/**
	 * Optional auto-scaling capability requirement. When <code>true</code>, auto-scale must be supported.
	 */
	private boolean autoScale;

	/**
	 * Indicates this instance is customizable.
	 *
	 * @return <code>true</code> when this instance is customizable.
	 */
	@JsonIgnore
	public boolean isCustom() {
		return cpu == 0;
	}

	/**
	 * Indicates the consumption of Watt for this instance. When <code>null</code>, the value is unknown.
	 */
	private Double watt;

	/**
	 * Indicates the consumption of Watt for this instance. When <code>null</code>, the value is unknown. This an array
	 * of 10% workload usage, from idle to 90%. Separator is <code>;</code>.
	 */
	private String watt10;
}
