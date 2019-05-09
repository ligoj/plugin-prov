/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.model.system.AbstractNamedValue;

import lombok.Getter;
import lombok.Setter;

/**
 * Tag for edition.
 */
@Getter
@Setter
public class TagEditionVo extends AbstractNamedValue<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

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
