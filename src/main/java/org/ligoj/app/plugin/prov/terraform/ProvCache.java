/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.util.function.Function;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;

import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.stereotype.Component;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.config.CacheConfig;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;

/**
 * Provisioning data cache configurations.
 */
@Component
public class ProvCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final Function<String, CacheConfig<?, ?>> provider) {
		cacheManager.createCache("terraform-version", provider.apply("terraform-version"));
		final var cfgTVL = provider.apply("terraform-version-latest")
				.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_DAY));
		cacheManager.createCache("terraform-version-latest", cfgTVL);

		final var cfgPL = provider.apply("prov-location")
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU)
						.setMaximumSizePolicy(EvictionConfig.MaxSizePolicy.ENTRY_COUNT).setSize(1000));
		cacheManager.createCache("prov-location", cfgPL);
		cacheManager.createCache("prov-license", provider.apply("prov-license"));
		cacheManager.createCache("prov-software", provider.apply("prov-software"));

		final var cfgPIT = provider.apply("prov-instance-type")
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU)
						.setMaximumSizePolicy(EvictionConfig.MaxSizePolicy.ENTRY_COUNT).setSize(1000));
		cacheManager.createCache("prov-instance-type", cfgPIT);
		cacheManager.createCache("prov-instance-type-dyn", provider.apply("prov-instance-type-dyn"));

		// Database caches
		cacheManager.createCache("prov-database-engine", provider.apply("prov-database-engine"));
		cacheManager.createCache("prov-database-edition", provider.apply("prov-database-edition"));
		cacheManager.createCache("prov-database-license", provider.apply("prov-database-license"));
	}

}
