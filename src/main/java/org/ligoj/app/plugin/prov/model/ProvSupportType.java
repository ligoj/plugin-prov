/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import lombok.Getter;
import lombok.Setter;

/**
 * A support plan configuration.
 *
 * @see <a href="https://cloud.google.com/support/#support-options">GCP</a>
 * @see <a href="https://azure.microsoft.com/en-us/support/plans/">Azure</a>
 * @see <a href="https://aws.amazon.com/premiumsupport/compare-plans/">AWS</a>
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_SUPPORT_TYPE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvSupportType extends AbstractCodedEntity {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * SLA starting date of support: phone and response time (milliseconds) starting from midnight. When
	 * <code>null</code>, there is no SLA.
	 */
	@PositiveOrZero
	private Long slaStartTime;

	/**
	 * SLA ending date of support: phone and response time (milliseconds) starting from midnight. When
	 * <code>null</code>, there is no SLA.
	 */
	@PositiveOrZero
	private Long slaEndTime;

	/**
	 * SLA apply to weekend.
	 */
	private boolean slaWeekEnd;

	/**
	 * Minimal commitment. One month means not commitment.
	 */
	@Positive
	private int commitment;

	/**
	 * API access. <code>null</code> when is not available.
	 */
	private SupportType accessApi;

	/**
	 * Email access. <code>null</code> when is not available.
	 */
	private SupportType accessEmail;

	/**
	 * Chat access. <code>null</code> when is not available.
	 */
	private SupportType accessChat;

	/**
	 * Phone access. <code>null</code> when is not available.
	 */
	private SupportType accessPhone;

	/**
	 * Who can open cases. When <code>null</code>, unlimited.
	 */
	@PositiveOrZero
	private Integer seats;

	/**
	 * Maximal duration (milliseconds) within the opened support for general guidance. <code>null</code> when is not
	 * available.
	 */
	@PositiveOrZero
	private Long slaGeneralGuidance;

	/**
	 * Maximal duration (milliseconds) within the opened support for system impaired. <code>null</code> when is not
	 * available.
	 */
	@PositiveOrZero
	private Long slaSystemImpaired;

	/**
	 * Maximal duration (milliseconds) within the opened support for production system impaired. <code>null</code> when
	 * is not available.
	 */
	@PositiveOrZero
	private Long slaProductionSystemImpaired;

	/**
	 * Maximal duration (milliseconds) within the opened support for production system down. <code>null</code> when is
	 * not available.
	 */
	@PositiveOrZero
	private Long slaProductionSystemDown;

	/**
	 * Maximal duration (milliseconds) within the opened support for Business-critical system down. <code>null</code>
	 * when is not available.
	 */
	@PositiveOrZero
	private Long slaBusinessCriticalSystemDown;

	/**
	 * Optional consulting services level: WORST=reserved, LOW=generalGuidance, MEDIUM=contextualGuidance,
	 * GOOD=contextualReview, BEST=reserved
	 */
	private Rate level;

}
