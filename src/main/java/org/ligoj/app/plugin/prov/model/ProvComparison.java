/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import lombok.Getter;
import lombok.Setter;

/**
 * Association between a main subscription (MS) and one of its compared subscriptions (CS). While the association exists,
 * the CS is kept as a synchronized clone of the MS: adds / updates / deletes on the MS are mirrored onto the CS. The MS
 * may have several CS.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_COMPARISON", uniqueConstraints = @UniqueConstraint(columnNames = { "mainSubscription",
		"subscription" }))
public class ProvComparison extends AbstractPersistable<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The main subscription (MS) driving the comparison.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	private Subscription mainSubscription;

	/**
	 * The compared subscription (CS), kept in sync with the MS.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	private Subscription subscription;

}
