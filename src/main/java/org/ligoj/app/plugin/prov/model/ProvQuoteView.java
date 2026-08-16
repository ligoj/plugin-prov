/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractDescribedAuditedEntity;
import org.ligoj.bootstrap.core.model.ToIdSerializer;

import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.annotation.JsonSerialize;

/**
 * A shared, named view of the quote screen: search + filters + columns + sort + display state, serialized as an opaque
 * JSON document owned by the UI. Stored per subscription and visible to every user of that subscription — the "shared"
 * counterpart of the browser-local saved views. The author and dates come from the audit columns.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_VIEW", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "subscription" }))
public class ProvQuoteView extends AbstractDescribedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The subscription this view belongs to.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private Subscription subscription;

	/**
	 * The serialized view state (opaque UI document). Small, so returned with the listing.
	 */
	@Lob
	private String data;
}
