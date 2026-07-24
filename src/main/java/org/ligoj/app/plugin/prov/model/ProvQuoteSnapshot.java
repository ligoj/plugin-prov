/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractDescribedAuditedEntity;
import org.ligoj.bootstrap.core.model.ToIdSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/**
 * An immutable, named snapshot of a quote configuration. The full configuration is stored as an opaque JSON document
 * (see the snapshot resource for its layout) so the snapshot stays decoupled from the live cost graph; the headline
 * figures are duplicated as columns for cheap listing. The label is the inherited {@code name}; author and creation
 * date come from the audit columns.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_SNAPSHOT")
public class ProvQuoteSnapshot extends AbstractDescribedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The snapshotted subscription.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private Subscription subscription;

	/**
	 * The serialized configuration document. Never sent with the listing — fetched explicitly by id.
	 */
	@Lob
	@JsonIgnore
	private String data;

	/**
	 * Quote cost figures at snapshot time.
	 */
	private double cost = 0d;
	private double maxCost = 0d;
	private double co2 = 0d;
	private double maxCo2 = 0d;

	/**
	 * Number of resources captured in the document.
	 */
	private int nbResources = 0;
}
