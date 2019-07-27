/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Size;

import org.ligoj.bootstrap.core.model.AbstractNamedAuditedEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A simple key with optional value
 */
@Getter
@Setter
public class TagVo extends AbstractNamedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Value as string.
	 */
	@Size(max = 1024)
	private String value;

	@Override
	public String toString() {
		return getName() + (getValue() == null ? "" : (":" + getValue()));
	}

}
