/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.support;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.TagVo;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Support configuration edition.
 */
@Getter
@Setter
public class QuoteSupportEditionVo extends DescribedBean<Integer> implements QuoteTagSupport {

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
	 * Optional consulting services level: WORST=reserved, LOW=generalGuidance, MEDIUM=contextualGuidance,
	 * GOOD=contextualReview, BEST=reserved
	 */
	private Rate level;

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

	/**
	 * The tags to override when not <code>null</code>.
	 */
	private List<TagVo> tags;

}
