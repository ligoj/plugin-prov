/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A tag on a resource. A tag is composed by a name and an optional value. The couple <code>name,value</code> is unique
 * for a resource. So a resource can have several tags having the same name but a different value.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractProvTag extends AbstractPersistable<Integer> implements INamableBean<Integer> {

	/**
	 * Tag key name and value pattern.
	 */
	public static final String PATTERN = "[\\p{L}\\d\\s+-=._/@&#']+";

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Object name
	 */
	@NotBlank
	@Pattern(regexp = AbstractProvTag.PATTERN)
	private String name;

	/**
	 * Value as string.
	 */
	@Size(max = 1024, min = 1)
	@Pattern(regexp = AbstractProvTag.PATTERN)
	@Column(length = 1024)
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

}
