/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.dao;

import java.util.List;

import javax.cache.annotation.CacheKey;
import javax.cache.annotation.CacheResult;

import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.Rate;

/**
 * {@link ProvDatabaseType} repository.
 */
public interface ProvDatabaseTypeRepository extends BaseProvInstanceTypeRepository<ProvDatabaseType> {

	@CacheResult(cacheName = "prov-database-type")
	@Override
	List<Integer> findValidTypes(@CacheKey String node, @CacheKey double cpu, @CacheKey double gpu,
			@CacheKey double ram, @CacheKey double limitCpu, @CacheKey double limitGpu, @CacheKey double limitRam,
			@CacheKey double baseline, @CacheKey boolean physical, @CacheKey int type, @CacheKey String processor,
			@CacheKey boolean autoScale, @CacheKey Rate cpuRate, @CacheKey Rate gpuRate, @CacheKey Rate ramRate,
			@CacheKey Rate networkRate, @CacheKey Rate storageRate, @CacheKey boolean edge, @CacheKey boolean co2Mode);

	@CacheResult(cacheName = "prov-database-type-dyn")
	@Override
	List<Integer> findDynamicTypes(@CacheKey String node, @CacheKey double baseline, @CacheKey boolean physical,
			@CacheKey int type, @CacheKey String processor, @CacheKey boolean autoScale, @CacheKey Rate cpuRate,
			@CacheKey Rate gpuRate, @CacheKey Rate ramRate, @CacheKey Rate networkRate, @CacheKey Rate storageRate,
			@CacheKey boolean edge, @CacheKey boolean co2Mode);

	@CacheResult(cacheName = "prov-database-type-has-dyn")
	@Override
	boolean hasDynamicalTypes(String node);

	@CacheResult(cacheName = "prov-database-has-co2")
	@Override
	boolean hasCo2Data(String node);
}
