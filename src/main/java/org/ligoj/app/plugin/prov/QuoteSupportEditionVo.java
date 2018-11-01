/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Support configuration edition.
 */
@Getter
@Setter
public class QuoteSupportEditionVo extends DescribedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Who can open cases. When <code>null</code>, unlimited requirement.
	 */
	@PositiveOrZero
	private Integer seats;

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
	 * General guidance.
	 */
	private boolean generalGuidance;

	/**
	 * Contextual guidance based on your use-case.
	 */
	private boolean contextualGuidance;

	/**
	 * Consultative review and guidance based on your applications.
	 */
	private boolean contextualReview;

	/**
	 * Related storage type name within the given location.
	 */
	@NotNull
	private String type;

	/**
	 * Related subscription identifier.
	 */
	@NotNull
	@Positive
	private Integer subscription;

}
