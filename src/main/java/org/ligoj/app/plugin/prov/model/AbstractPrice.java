package org.ligoj.app.plugin.prov.model;

import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.AbstractNamedEntity;
import org.ligoj.bootstrap.core.model.AbstractPersistable;
import org.ligoj.bootstrap.core.model.ToNameSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Resource price context.
 */
@Getter
@Setter
@MappedSuperclass
@ToString(of = { "cost", "type", "location" })
public abstract class AbstractPrice<T extends AbstractNamedEntity<?>> extends AbstractPersistable<Integer> {

	/**
	 * The cost of the resource. The meaning of this value depends on the implementing class.
	 */
	private double cost = 0;

	/**
	 * Optional location constraint.
	 */
	@ManyToOne
	@JsonSerialize(using = ToNameSerializer.class)
	private ProvLocation location;

	/**
	 * The related resource type.
	 */
	@NotNull
	@ManyToOne
	private T type;

}
