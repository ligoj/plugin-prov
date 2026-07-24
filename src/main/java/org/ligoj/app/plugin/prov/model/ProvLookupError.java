/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;
import org.ligoj.bootstrap.core.model.ToIdSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/**
 * A resource of a main subscription (MS) that a compared subscription (CS) could not reproduce: its import or price
 * lookup failed (no matching offer in the CS catalog) while cloning or mirroring. The resource is ignored on the CS but
 * tracked here so the comparison UI can surface the gap.
 * <p>
 * {@link #name} and {@link #description} are copied from the source request. {@link #subscription} points to the CS that
 * failed. {@link #mainSubscription} is the optional MS the resource came from. {@link #mainResourceId} is the id of the
 * source resource on the MS — intentionally NOT a foreign key (the MS resource may be edited or deleted independently);
 * it is only a soft link used to line the error up with its MS resource in the UI.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_LOOKUP_ERROR")
public class ProvLookupError extends AbstractDescribedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The compared subscription (CS) that could not handle the resource.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private Subscription subscription;

	/**
	 * The optional main subscription (MS) the resource originates from.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private Subscription mainSubscription;

	/**
	 * The type of the failed resource.
	 */
	@NotNull
	@Enumerated(EnumType.STRING)
	private ResourceType resourceType;

	/**
	 * The identifier of the source resource on the MS. Soft link (not a foreign key) used to align this error with its
	 * MS resource in the comparison UI.
	 */
	private Integer mainResourceId;

}
