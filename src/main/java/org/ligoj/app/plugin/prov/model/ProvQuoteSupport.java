/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A support plan requirement.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_SUPPORT", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteSupport extends AbstractQuoteResource<ProvSupportPrice> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * SLA starting date of support: phone and response time (milliseconds) starting from midnight. May be
	 * <code>null</code> when no requirement.
	 */
	@PositiveOrZero
	private Long slaStartTime;

	/**
	 * SLA ending date of support: phone and response time (milliseconds) starting from midnight. May be
	 * <code>null</code> when no requirement.
	 */
	@PositiveOrZero
	private Long slaEndTime;

	/**
	 * SLA apply to weekend.
	 */
	private boolean slaWeekEnd;

	/**
	 * API access. <code>null</code> when is not required.
	 */
	private SupportType accessApi;

	/**
	 * Email access. <code>null</code> when is not required.
	 */
	private SupportType accessEmail;

	/**
	 * Chat access. <code>null</code> when is not required.
	 */
	private SupportType accessChat;

	/**
	 * Phone access. <code>null</code> when is not required.
	 */
	private SupportType accessPhone;

	/**
	 * Who can open cases. When <code>null</code>, unlimited requirement.
	 */
	@PositiveOrZero
	private Integer seats;

	/**
	 * Optional consulting services level: WORST=reserved, LOW=generalGuidance, MEDIUM=contextualGuidance,
	 * GOOD=contextualReview, BEST=reserved
	 */
	private Rate level;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvSupportPrice price;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return getConfiguration().isUnboundCost();
	}

	@Override
	public ResourceType getResourceType() {
		return ResourceType.SUPPORT;
	}
}
