/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvInstanceType;

/**
 * {@link ProvInstanceType} repository.
 */
public interface ProvInstanceTypeRepository extends BaseProvInstanceTypeRepository<ProvInstanceType> {

	@CacheResult(cacheName = "prov-instance-type-dyn")
	@Override
	List<Integer> findDynamicTypes(@CacheKey String node, @CacheKey Boolean constant, @CacheKey Boolean physical,
			@CacheKey Integer type, @CacheKey String processor);

	@CacheResult(cacheName = "prov-instance-type")
	@Override
	List<Integer> findValidTypes(@CacheKey String node, @CacheKey double cpu, @CacheKey int ram,
			@CacheKey Boolean constant, @CacheKey Boolean physical, @CacheKey Integer type, @CacheKey String processor);

	@CacheResult(cacheName = "prov-instance-type-has-dyn")
	@Override
	boolean hasDynamicalTypes(String node);
}
