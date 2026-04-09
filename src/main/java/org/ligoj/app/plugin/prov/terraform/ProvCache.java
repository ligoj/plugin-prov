/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import com.hazelcast.cache.HazelcastCacheManager;
import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.EvictionPolicy;
import org.ligoj.bootstrap.resource.system.cache.CacheConfigurer;
import org.ligoj.bootstrap.resource.system.cache.CacheManagerAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import javax.cache.expiry.Duration;
import java.util.stream.Stream;

/**
 * Provisioning data cache configurations.
 */
@Component
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
public class ProvCache implements CacheManagerAware {

	@Override
	public void onCreate(final HazelcastCacheManager cacheManager, final CacheConfigurer configurer) {
		cacheManager.createCache("terraform-version", configurer.newCacheConfig("terraform-version"));
		final var cfgTVL = configurer.newCacheConfig("terraform-version-latest",Duration.ONE_DAY);
		cacheManager.createCache("terraform-version-latest", cfgTVL);

		final var cfgPL = configurer.newCacheConfig("prov-location")
				.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setSize(1000));
		cacheManager.createCache("prov-location", cfgPL);

		// Instance cache configurations
		createCacheEvict(cacheManager, configurer, "prov-instance-type", "prov-instance-type-dyn",
				"prov-instance-type-has-dyn", "prov-instance-has-co2", "prov-instance-term", "prov-database-type",
				"prov-database-type-dyn", "prov-database-type-has-dyn", "prov-database-has-co2", "prov-container-type",
				"prov-container-type-dyn", "prov-container-type-has-dyn", "prov-container-has-co2",
				"prov-function-type", "prov-function-type-dyn", "prov-function-type-has-dyn", "prov-function-has-co2");
		createCache(cacheManager, configurer, "prov-processor", "prov-instance-software", "prov-instance-license",
				"prov-instance-os", "prov-database-engine", "prov-database-edition", "prov-database-license",
				"prov-container-license", "prov-container-os", "prov-architecture");

	}

	private void createCache(final HazelcastCacheManager cacheManager,
			final CacheConfigurer configurer, final String... names) {
		Stream.of(names).forEach(name -> cacheManager.createCache(name, configurer.newCacheConfig(name)));
	}

	private void createCacheEvict(final HazelcastCacheManager cacheManager,
			final CacheConfigurer configurer, final String... names) {
		Stream.of(names).forEach(name -> {
			final var cfgPIT = configurer.newCacheConfig(name)
					.setEvictionConfig(new EvictionConfig().setEvictionPolicy(EvictionPolicy.LRU).setSize(1000));
			cacheManager.createCache(name, cfgPIT);
		});
	}
}
