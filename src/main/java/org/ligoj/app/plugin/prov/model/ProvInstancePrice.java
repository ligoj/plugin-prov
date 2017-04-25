package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.AbstractPersistable;

import lombok.Getter;
import lombok.Setter;

/**
 * An priced instance with billing configuration
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_INSTANCE_PRICE", uniqueConstraints = @UniqueConstraint(columnNames = { "instance", "os",
		"type" }))
public class ProvInstancePrice extends AbstractPersistable<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * The hourly cost of this instance.
	 */
	@NotNull
	private Double cost;
	
	/**
	 * The optional hourly cost of one requested CPU.
	 */
	private Double costCpu;
	
	/**
	 * The optional hourly cost of one requested GB memory.
	 */
	private Double costRam;
	
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
