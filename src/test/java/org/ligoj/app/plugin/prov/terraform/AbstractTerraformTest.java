/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.BiFunction;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.resource.plugin.LigojPluginsClassLoader;
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base Terraform test class.
 */
public abstract class AbstractTerraformTest extends AbstractAppTest {
	protected static final File TEST_LOGS = new File("target/test-classes/terraform-logs").getAbsoluteFile();

	protected static final File MOCK_PATH = new File("target/test-classes/terraform-it").getAbsoluteFile();

	protected int subscription;

	@Autowired
	protected ConfigurationResource configuration;

	@Autowired
	protected TerraformRunnerResource runner;
	@Autowired
	protected TerraformUtils utils;

	@AfterEach
	@BeforeEach
	public void cleanupFiles() throws IOException {
		FileUtils.deleteDirectory(MOCK_PATH);
		FileUtils.forceMkdir(MOCK_PATH);
	}

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class, SystemConfiguration.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		cacheManager.getCache("terraform-version-latest").clear();
		cacheManager.getCache("terraform-version").clear();
	}

	protected Subscription getSubscription() {
		return em.find(Subscription.class, subscription);
	}

	protected TerraformUtils newTerraformUtils(final BiFunction<Subscription, String[], File> toFile,
			final String... customArgs) {
		final var classLoader = Mockito.mock(LigojPluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(MOCK_PATH.toPath());

		// Replace the CLI runner
		TerraformUtils utils = new TerraformUtils() {

			@Override
			public ProcessBuilder newBuilder(String... args) {
				return new ProcessBuilder(ArrayUtils.addAll(
						new String[] { "java", "-cp", MOCK_PATH.getParent(),
								"org.ligoj.app.plugin.prov.terraform.Main" },
						customArgs.length > 0 ? customArgs : args));
			}

			@Override
			protected boolean isInstalled() {
				return true;
			}

			@Override
			protected LigojPluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			public String getLatestVersion() {
				return "2.0.0";
			}

			@Override
			public File toFile(final Subscription subscription, final String... file) {
				return toFile.apply(subscription, file);
			}
		};
		utils.configuration = configuration;
		return utils;
	}
}
