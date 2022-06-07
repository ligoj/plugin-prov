/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Column;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

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
	@Column(columnDefinition = "double default 0")
	private double co2 = 0;
}
