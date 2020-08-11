/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.hibernate.validator.constraints.Length;
import org.ligoj.app.plugin.prov.TagVo;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.core.IDescribableBean;
import org.ligoj.bootstrap.core.validation.SafeHtml;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

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
	 * The tags to override when not <code>null</code>.
	 */
	private List<TagVo> tags;

	@Override
	@JsonIgnore
	public String getLocationName() {
		return getLocation();
	}
}
