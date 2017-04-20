package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.app.api.NodeScoped;
import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Storage configuration
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_STORAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvStorage extends AbstractDescribedEntity<Integer> implements NodeScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * The monthly cost of 1Go (Giga Bytes).
	 */
	@NotNull
	private Double cost;

	/**
	 * The monthly cost.
	 */
	@NotNull
	private VmStorageType type;

	/**
	 * The minimal disk in "Go".
	 */
	@NotNull
	private Integer minimal;

	/**
	 * The maximum supported size in "Go". May be <code>null</code>.
	 */
	private Integer maximal;

	/**
	 * The cost per transaction. May be 0.
	 */
	private double transactionalCost;

	/**
	 * The enabled provider.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;

}
