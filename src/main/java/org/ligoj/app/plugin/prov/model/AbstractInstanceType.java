/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance with characteristics
 */
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractInstanceType extends AbstractDescribedEntity<Integer> implements ProvType {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Amount of CPU. When <code>0</code>, correspond to a custom instance.
	 */
	@NotNull
	private Double cpu;

	/**
	 * RAM (Memory) in "MiB" = "Mega Bytes"
	 */
	@NotNull
	private Integer ram;

	/**
	 * Optional physical processor. May be <code>null</code>.
	 */
	private String processor;

	/**
	 * When <code>true</code>, this instance type is physical, not virtual.
	 */
	private Boolean physical = false;

	/**
	 * CPU performance.
	 */
	@NotNull
	@Enumerated(EnumType.ORDINAL)
	private Rate cpuRate = Rate.MEDIUM;

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
	 * When <code>true</code> the delivery power is constant over time. Otherwise, is variable.
	 */
	@NotNull
	private Boolean constant;

	/**
	 * The related node (VM provider) of this instance.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;

	/**
	 * Indicates this instance is customizable.
	 *
	 * @return <code>true</code> when this instance is customizable.
	 */
	@JsonIgnore
	public boolean isCustom() {
		return cpu == 0;
	}

}
