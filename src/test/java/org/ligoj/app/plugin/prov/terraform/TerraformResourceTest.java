package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import javax.transaction.Transactional;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.awaitility.Awaitility;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link TerraformResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class TerraformResourceTest extends AbstractAppTest {
	private static final File MOCK_PATH = new File("target/test-classes/terraform-test").getAbsoluteFile();

	private int subscription;

	@Autowired
	private TerraformResource resource;

	@Autowired
	private TerraformRunnerResource runner;

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
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class,
				ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
				ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
	}

	@Test
	public void getTerraformNotSupported() {
		Assertions.assertEquals("terraform-no-supported", Assertions.assertThrows(BusinessException.class, () -> {
			newResource(null).getTerraform(subscription, "any.tf");
		}).getMessage());
	}

	@Test
	public void getTerraform() throws IOException {
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		((StreamingOutput) newResource(terraforming).getTerraform(subscription, "any.tf").getEntity())
				.write(new ByteArrayOutputStream());
		Mockito.verify(terraforming).terraform(ArgumentMatchers.any(OutputStream.class),
				ArgumentMatchers.eq(subscription), ArgumentMatchers.any(QuoteVo.class));

		// Coverage only
		Assertions.assertEquals(TerraformStep.PLAN, TerraformStep.valueOf(TerraformStep.values()[0].name()));
	}

	/**
	 * IOException during the asynchronous execution
	 */
	@Test
	public void applyTerraformError() {
		final TerraformResource resource = new TerraformResource() {

			@Override
			protected File applyTerraform(final Subscription entity, final Terraforming terra,
					final QuoteVo configuration) throws IOException {
				throw new IOException();
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Mock to disable inner transactions for this test
		resource.resource = Mockito.mock(ProvResource.class);
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);

		// Replace the plugin locator
		resource.locator = locator;
		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class))
				.thenReturn(Mockito.mock(Terraforming.class));
		resource.applyTerraform(subscription);
	}

	@Test
	public void applyTerraform() {
		newResource(Mockito.mock(Terraforming.class)).applyTerraform(subscription);
	}

	private Subscription getSubscription() {
		return em.find(Subscription.class, subscription);
	}

	private void applyTerraform(final TerraformResource resource) throws IOException, InterruptedException {
		resource.applyTerraform(getSubscription(), Mockito.mock(Terraforming.class), null);
	}

	@Test
	public void applyTerraformInternal() throws IOException, InterruptedException {
		final File log = new File(MOCK_PATH, "main.log");
		final File tf = new File(MOCK_PATH, "main.tf");
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false);
		applyTerraform(resource);

		// Synchronization of sub processes
		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.until(() -> tf.exists() && log.exists() && IOUtils.toString(log.toURI(), "UTF-8").contains("show"));
		Thread.yield();

		Assertions.assertTrue(tf.exists());
		Assertions.assertTrue(log.exists());
		final String logString = IOUtils.toString(log.toURI(), "UTF-8");
		Assertions.assertTrue(logString.contains("plan"));
		Assertions.assertTrue(logString.contains("apply"));
		Assertions.assertTrue(logString.contains("show"));

		// Check the log file is well handled
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		((StreamingOutput) resource.getTerraformLog(subscription).getEntity()).write(bos);
		Assertions.assertEquals(logString, bos.toString(StandardCharsets.UTF_8));

		// Check the task status
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertTrue(task.isFinished());
		Assertions.assertFalse(task.isFailed());
		Assertions.assertNotNull(task.getStart());
		Assertions.assertNotNull(task.getAuthor());
		Assertions.assertEquals(DEFAULT_USER, task.getAuthor());
		Assertions.assertEquals("service:prov:test:account", task.getLocked().getId());
		Assertions.assertNotNull(task.getEnd());
		Assertions.assertEquals(TerraformStep.SHOW, task.getStep());
	}

	@Test
	public void applyTerraformMainWriteFailed() {
		applyTerraformIOE(
				newResource(Mockito.mock(Terraforming.class), (s, f) -> new File("random-place/random-place"), false));
	}

	private void applyTerraformIOE(final TerraformResource resource) {
		Assertions.assertThrows(IOException.class, () -> {
			applyTerraform(resource);
		});
		// Nice, as expected, but more check to do
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertNotNull(task);
		Assertions.assertTrue(task.isFinished());
		Assertions.assertTrue(resource.runner.getTask("service:prov:test:account").isFailed());
	}

	@Test
	public void applyTerraformLogWriteFailed() {
		applyTerraformIOE(newResource(Mockito.mock(Terraforming.class),
				(s, f) -> f.length > 0 && f[0].equals("main.log") ? new File("random-place/random-place")
						: f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]),
				false));
	}

	@Test
	public void applyTerraformExit0() throws Exception {
		applyTerraformExit(0, "Terraform exit code 0 -> no need to continue");
	}

	@Test
	public void getVersion() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0",
				"Terraform v0.0.1");
		Assertions.assertEquals("0.0.1", resource.getVersion());
	}

	@Test
	public void getVersionWrongOutput() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0", "WHAT?");
		Assertions.assertNull(resource.getVersion());
	}

	@Test
	public void getVersionExit1() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=1");
		Assertions.assertNull(resource.getVersion());
	}

	@Test
	public void applyTerraformExit1() {
		Assertions.assertEquals("aborted", Assertions.assertThrows(BusinessException.class, () -> {
			applyTerraformExit(1, "Terraform exit code 1 -> aborted");
		}).getMessage());
	}

	private void applyTerraformExit(final int code, final String message) throws Exception {
		final File log = new File(MOCK_PATH, "main.log");
		final File tf = new File(MOCK_PATH, "main.tf");
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=" + code);
		Exception thrown = null;
		try {
			applyTerraform(resource);
		} catch (Exception e) {
			thrown = e;
		}

		// Synchronization of sub processes
		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.until(() -> tf.exists() && log.exists() && IOUtils.toString(log.toURI(), "UTF-8").contains("error="));
		Thread.yield();

		Assertions.assertTrue(tf.exists());
		Assertions.assertTrue(log.exists());
		final String logString = IOUtils.toString(log.toURI(), "UTF-8");
		Assertions.assertTrue(logString.contains("error=" + code));
		Assertions.assertTrue(logString.contains(message));
		Assertions.assertTrue(resource.runner.getTask("service:prov:test:account").isFinished());

		if (code == 1) {
			Assertions.assertTrue(resource.runner.getTask("service:prov:test:account").isFailed());
		} else {
			Assertions.assertFalse(resource.runner.getTask("service:prov:test:account").isFailed());
		}
		if (thrown != null) {
			throw thrown;
		}
	}

	@Test
	public void getTerraformLog() throws IOException {
		Assertions.assertEquals(404,
				newResource(Mockito.mock(Terraforming.class)).getTerraformLog(subscription).getStatus());
	}

	@Test
	public void toFile() throws IOException {
		ThreadClassLoaderScope scope = null;
		try {
			final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
			scope = new ThreadClassLoaderScope(new URLClassLoader(new URL[0], classLoader));
			final File file = new File("");
			final Subscription entity = new Subscription();
			entity.setId(15);
			Mockito.when(classLoader.toFile(entity, "15", "some")).thenReturn(file);
			Assertions.assertSame(file, resource.toFile(entity, "some"));
			Assertions.assertNotNull(PluginsClassLoader.getInstance());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	private TerraformResource newResource(final Terraforming providerResource, final String... customArgs) {
		return newResource(providerResource, true, customArgs);
	}

	private TerraformResource newResource(final Terraforming providerResource, final boolean dryRun,
			final String... customArgs) {
		return newResource(providerResource, (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]), dryRun,
				customArgs);
	}

	private TerraformResource newResource(final Terraforming providerResource,
			final BiFunction<Subscription, String[], File> toFile, final boolean dryRun, final String... customArgs) {
		final TerraformResource resource = new TerraformResource() {
			@Override
			protected File toFile(final Subscription subscription, final String file) {
				return toFile.apply(subscription, new String[] { file });
			}

			/**
			 * Prepare the Terraform environment to apply the new environment. Note there is no concurrency check.
			 */
			@Override
			protected File applyTerraform(final Subscription entity, final Terraforming terra,
					final QuoteVo configuration) throws IOException, InterruptedException {
				if (dryRun) {
					// Ignore this call
					return null;
				}
				return super.applyTerraform(entity, terra, configuration);
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the plugin locator
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);
		resource.locator = locator;

		// Replace the runner
		resource.runner = new TerraformRunnerResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.runner);

		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class)).thenReturn(providerResource);
		final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(MOCK_PATH.toPath());

		// Replace the CLI runner
		resource.terraformUtils = new TerraformUtils() {

			@Override
			public ProcessBuilder newBuilder(String... args) {
				return new ProcessBuilder(ArrayUtils.addAll(
						new String[] { "java", "-cp", MOCK_PATH.getParent(),
								"org.ligoj.app.plugin.prov.terraform.Main" },
						customArgs.length > 0 ? customArgs : args));
			}

			@Override
			protected PluginsClassLoader getClassLoader() {
				return classLoader;
			}
		};
		return resource;
	}

	@Test
	public void testBusiness() {
		// Coverage only
		Assertions.assertEquals(InternetAccess.PUBLIC.ordinal(),
				InternetAccess.valueOf(InternetAccess.values()[0].name()).ordinal());
		Assertions.assertNotNull(runner.getNodeRepository());
		Assertions.assertNotNull(runner.getTaskRepository());
	}
}
