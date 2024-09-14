/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;
import org.ligoj.app.plugin.prov.TagVo;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.validation.SafeHtml;

import java.util.List;

/**
 * Storage configuration edition.
 */
@Getter
@Setter
public class QuoteStorageEditionVo extends QuoteStorageQuery implements IDescribableBean<Integer>, QuoteTagSupport {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Identifier of this bean.
	 */
	private Integer id;

	@NotBlank
	private String name;

	@Length(max = 250)
	@SafeHtml
	private String description;

	/**
	 * Related storage type code within the given location.
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
	 * The maximal used size. When <code>null</code>, the requested size is used.
	 *
	 * @see #sizeMax
	 */
	@Positive
	private Integer sizeMax;

	/**
	 * The quantity of this instance. When <code>null</code>, is considered as <code>1</code>.
	 */
	@Positive
	private Integer quantity = 1;

	/**
	 * The tags to override when not <code>null</code>.
	 */
	private List<TagVo> tags;

}
