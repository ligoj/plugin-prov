/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A tag on a resource. A tag is composed by a name and an optional value. The couple <code>name,value</code> is unique
 * for a resource. So a resource can have several tags having the same name but a different value.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TAG", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "value", "type",
		"resource", "configuration" }))
public class ProvTag extends AbstractPersistable<Integer>
		implements Configurable<ProvQuote, Integer>, INamableBean<Integer> {

	/**
	 * Tag key name and value pattern.
	 */
	public static final String PATTERN = "[\\-_./a-zA-Z0-9]+\\s[\\-_./a-zA-Z0-9]*";

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Object name
	 */
	@NotBlank
	@Pattern(regexp = ProvTag.PATTERN)
	private String name;

	/**
	 * Value as string.
	 */
	@Size(max = 1024, min = 1)
	@Pattern(regexp = ProvTag.PATTERN)
	private String value;

	/**
	 * The attached resource type.
	 */
	@NotNull
	private ResourceType type;

	/**
	 * The related resource identifier. This is not a strong relationship, and this is intended. We don't want to handle
	 * one column per resource type.
	 */
	@NotNull
	private Integer resource;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

}
