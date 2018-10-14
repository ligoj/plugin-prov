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
		final CacheConfig<?, ?> tokens = provider.apply("terraform-version-latest");
		tokens.setExpiryPolicyFactory(ModifiedExpiryPolicy.factoryOf(Duration.ONE_DAY));
		cacheManager.createCache("terraform-version-latest", tokens);

		final CacheConfig<?, ?> tokens2 = provider.apply("prov-location");
		tokens2.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU)
				.setMaximumSizePolicy(EvictionConfig.MaxSizePolicy.ENTRY_COUNT).setSize(1000));
		cacheManager.createCache("prov-location", tokens2);
		cacheManager.createCache("prov-license", provider.apply("prov-license"));
		cacheManager.createCache("prov-software", provider.apply("prov-software"));
	}

}
