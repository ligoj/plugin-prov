/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * Flag the type with CO2 capability having partial data.
 */
@NoRepositoryBean
public interface Co2Type {

	/**
	 * Return <code>true</code> when there is at least one type having CO2 data in this repository.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return <code>true</code> when there is at least one type having CO2 data in this repository.
	 */
	boolean hasCo2Data(String node);

}
