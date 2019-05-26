/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.Serializable;

import org.ligoj.bootstrap.model.system.AbstractNamedValue;

import lombok.Getter;
import lombok.Setter;

/**
 * A simple key with optional value
 */
@Getter
@Setter
public class TagVo extends AbstractNamedValue<Integer> implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		return getName() + (getValue() == null ? "" : (":" + getValue()));
	}

}
