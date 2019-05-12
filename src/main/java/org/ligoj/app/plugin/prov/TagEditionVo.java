/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.model.AbstractNamedAuditedEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * Tag for edition.
 */
@Getter
@Setter
public class TagEditionVo extends AbstractNamedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Value as string.
	 */
	@Size(max = 1024, min = 1)
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
