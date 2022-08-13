/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.util.function.Function;
import java.util.stream.Stream;

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
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setSize(1000));
		cacheManager.createCache("prov-location", cfgPL);

		// Instance cache configurations
		createCacheEvict(cacheManager, provider, "prov-instance-type", "prov-instance-type-dyn",
				"prov-instance-type-has-dyn", "prov-instance-term", "prov-database-type", "prov-database-type-dyn",
				"prov-database-type-has-dyn", "prov-container-type", "prov-container-type-dyn",
				"prov-container-type-has-dyn", "prov-function-type", "prov-function-type-dyn",
				"prov-function-type-has-dyn");
		createCache(cacheManager, provider, "prov-processor", "prov-instance-software", "prov-instance-license",
				"prov-instance-os", "prov-database-engine", "prov-database-edition", "prov-database-license",
				"prov-container-license", "prov-container-os");

	}

	private void createCache(final HazelcastCacheManager cacheManager,
			final Function<String, CacheConfig<?, ?>> provider, final String... names) {
		Stream.of(names).forEach(name -> cacheManager.createCache(name, provider.apply(name)));
	}

	private void createCacheEvict(final HazelcastCacheManager cacheManager,
			final Function<String, CacheConfig<?, ?>> provider, final String... names) {
		Stream.of(names).forEach(name -> {
			final var cfgPIT = provider.apply(name)
					.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setSize(1000));
			cacheManager.createCache(name, cfgPIT);
		});
	}
}
