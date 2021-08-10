/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import org.ligoj.app.api.NodeScoped;
import org.ligoj.bootstrap.core.IDescribableBean;

/**
 * Resource type specifications.
 */
public interface ProvType extends NodeScoped, IDescribableBean<Integer> {

	/**
	 * Return the code of the type.
	 *
	 * @return The code of the type.
	 */
	String getCode();
}
