/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractTermPriceVmOs;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * {@link AbstractTermPriceVmOs} repository.
 *
 * @param <T> The instance type type.
 * @param <P> The price type.
 */
@NoRepositoryBean
public interface BaseProvTermPriceOsRepository<T extends AbstractInstanceType, P extends AbstractTermPriceVmOs<T>>
		extends BaseProvTermPriceRepository<T, P> {

	/**
	 * Return all licenses related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @param os   The filtered OS.
	 * @return The filtered licenses.
	 */
	@Query("""
			SELECT DISTINCT(ip.license) FROM #{#entityName} ip INNER JOIN ip.type AS i
			  WHERE (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%')) AND ip.os=:os ORDER BY ip.license
			""")
	List<String> findAllLicenses(String node, VmOs os);

	/**
	 * Return all OS related to given node identifier.
	 *
	 * @param node The node linked to the subscription. Is a node identifier within a provider.
	 * @return The filtered OS.
	 */
	@Query("""
			SELECT DISTINCT(ip.os) FROM #{#entityName} ip INNER JOIN ip.type AS i
			  WHERE ip.os IS NOT NULL AND (:node = i.node.id OR :node LIKE CONCAT(i.node.id,':%'))
			  ORDER BY ip.os
			""")
	List<String> findAllOs(@CacheKey String node);

}
