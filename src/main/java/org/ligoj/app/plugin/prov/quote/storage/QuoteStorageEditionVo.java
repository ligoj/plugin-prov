/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import java.util.List;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.SafeHtml;
import org.hibernate.validator.constraints.SafeHtml.Attribute;
import org.hibernate.validator.constraints.SafeHtml.Tag;
import org.ligoj.app.plugin.prov.TagVo;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.core.IDescribableBean;

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
	@SafeHtml(additionalTagsWithAttributes = @Tag(name = "a", attributesWithProtocols = @Attribute(name = "href", protocols = "#")))
	private String description;

	/**
	 * Size of the storage in "GiB" "Gibi Bytes"
	 *
	 * @see <a href="https://en.wikipedia.org/wiki/Gibibyte">Gibibyte</a>
	 */
	@Positive
	private int size;

	/**
	 * Optional linked quoted instance.
	 */
	private Integer quoteInstance;

	/**
	 * Optional linked quoted instance.
	 */
	private Integer quoteDatabase;

	/**
	 * Optional location constraint.
	 */
	private String location;

	/**
	 * Optional required latency class.
	 */
	private Rate latency;

	/**
	 * Optional required optimized best usage of this storage
	 */
	private ProvStorageOptimized optimized;

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

	@Override
	public Integer getInstance() {
		return getQuoteInstance();
	}

	@Override
	@JsonIgnore
	public Integer getDatabase() {
		return getQuoteDatabase();
	}

	@Override
	@JsonIgnore
	public String getLocationName() {
		return getLocation();
	}
}
