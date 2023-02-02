/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.ColumnDefault;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Resource price context.
 *
 * @param <T> Resource type.
 */
@Getter
@Setter
@MappedSuperclass
@ToString(of = { "cost", "type", "location" })
public abstract class AbstractPrice<T extends ProvType> extends AbstractPersistable<Integer> {

	/**
	 * The internal offer code.
	 */
	private String code;

	/**
	 * The monthly cost of the resource. The meaning of this value depends on the implementing class.
	 */
	private double cost = 0;

	/**
	 * Optional location constraint.
	 */
	@ManyToOne
	@JsonIgnore
	private ProvLocation location;

	/**
	 * The related resource type.
	 */
	@NotNull
	@ManyToOne
	private T type;

	/**
	 * Indicates the consumption of carbon(co2) for this instance.
	 */
	@ColumnDefault("0")
	private double co2 = 0;

	/**
	 * The optional monthly CO2 consumption of one requested CPU with an array of 10% workload usage, from idle to 90%.
	 * Required for dynamic instance type. Separator is <code>;</code>.
	 */
	private String co210 = null;
}
