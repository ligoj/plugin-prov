package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
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
 * Storage type of a provider.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_STORAGE_TYPE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvStorageType extends AbstractDescribedEntity<Integer> implements NodeScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * The fixed monthly cost whatever the used size.
	 */
	private double cost = 0;

	/**
	 * The monthly cost of 1Go (Giga Bytes).
	 */
	private double costGb = 0;

	/**
	 * The frequency access
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	private ProvStorageFrequency frequency;

	/**
	 * Optimized best usage of this storage
	 */
	@Enumerated(EnumType.STRING)
	private ProvStorageOptimized optimized;

	/**
	 * The minimal disk size in "Go".
	 */
	private int minimal = 1;

	/**
	 * The maximum supported size in "Go". May be <code>null</code>.
	 */
	private Integer maximal;

	/**
	 * <code>true</code> when this storage can attached to an instance.
	 */
	private boolean instanceCompatible = false;

	/**
	 * The cost per transaction. May be <code>0</code>.
	 */
	private double costTransaction;

	/**
	 * The enabled provider.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;
}
