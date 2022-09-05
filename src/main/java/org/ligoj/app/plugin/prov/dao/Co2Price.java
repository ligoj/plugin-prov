/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import org.springframework.data.repository.NoRepositoryBean;

/**
 * Flag the price with CO2 capability having partial data.
 */
@NoRepositoryBean
public interface Co2Price {

	/**
	 * Return the amount of prices having a defined CO2 data set.
	 *
	 * @param node The node identifier linked to the subscription. Is a node identifier within a provider.
	 * @return The amount of prices having a defined CO2 data set.
	 */
	int countCo2DataByNode(String node);

}
