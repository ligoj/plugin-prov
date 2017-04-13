package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import lombok.Getter;
import lombok.Setter;

/**
 * An priced instance with billing configuration
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_VM_INSTANCE_PRICE", uniqueConstraints = @UniqueConstraint(columnNames = { "instance", "os",
		"mode" }))
public class ProvInstancePrice extends AbstractPersistable<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * The hourly cost.
	 */
	@NotNull
	@Min(0)
	private Double cost;

	/**
	 * Related instance characteristics.
	 */
	@NotNull
	@ManyToOne
	private ProvInstance instance;

	@NotNull
	@ManyToOne
	private ProvInstancePriceType type;

	/**
	 * The related price
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	private VmOs os;

}
