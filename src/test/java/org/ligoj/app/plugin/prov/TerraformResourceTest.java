package org.ligoj.app.plugin.prov;

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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link TerraformResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class TerraformResourceTest extends AbstractAppTest {
	private static final File MOCK_PATH = new File("target/test-classes/terraform-test").getAbsoluteFile();

	private int subscription;

	@Autowired
	private TerraformResource resource;

	@After
	@Before
	public void cleanupFiles() throws IOException {
		FileUtils.deleteDirectory(MOCK_PATH);
		FileUtils.forceMkdir(MOCK_PATH);
	}

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvQuote.class, ProvStorageType.class,
				ProvInstancePriceType.class, ProvInstance.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
	}

	@Test(expected = BusinessException.class)
	public void getTerraformNotSupported() {
		newResource(null).getTerraform(subscription, "any.tf");
	}

	@Test
	public void getTerraform() throws IOException {
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		((StreamingOutput) newResource(terraforming).getTerraform(subscription, "any.tf").getEntity()).write(new ByteArrayOutputStream());
		Mockito.verify(terraforming).terraform(Mockito.any(OutputStream.class), Mockito.eq(subscription), Mockito.any(QuoteVo.class));

		// Coverage only
		Assert.assertEquals(TerraformStep.PLAN, TerraformStep.valueOf(TerraformStep.values()[0].name()));
	}

	/**
	 * IOException during the asynchronous execution
	 */
	@Test
	public void applyTerraformError() {
		final TerraformResource resource = new TerraformResource() {

			@Override
			protected File applyTerraform(final Subscription entity, final Terraforming terra, final QuoteVo configuration)
					throws IOException, InterruptedException {
				throw new IOException();
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Mock to disable inner transactions for this test
		resource.resource = Mockito.mock(ProvResource.class);
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);

		// Replace the plugin locator
		resource.locator = locator;
		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class)).thenReturn(Mockito.mock(Terraforming.class));
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

		Assert.assertTrue(tf.exists());
		Assert.assertTrue(log.exists());
		final String logString = IOUtils.toString(log.toURI(), "UTF-8");
		Assert.assertTrue(logString.contains("plan"));
		Assert.assertTrue(logString.contains("apply"));
		Assert.assertTrue(logString.contains("show"));

		// Check the log file is well handled
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		((StreamingOutput) resource.getTerraformLog(subscription).getEntity()).write(bos);
		Assert.assertEquals(logString, bos.toString(StandardCharsets.UTF_8));

		// Check the task status
		TerraformStatus task = resource.resource.getTask(subscription);
		Assert.assertTrue(task.isFinished());
		Assert.assertFalse(task.isFailed());
		Assert.assertNotNull(task.getStart());
		Assert.assertNotNull(task.getAuthor());
		Assert.assertEquals(DEFAULT_USER, task.getAuthor());
		Assert.assertEquals(subscription, task.getSubscription().getId().intValue());
		Assert.assertNotNull(task.getEnd());
		Assert.assertEquals(TerraformStep.SHOW, task.getStep());
	}

	@Test
	public void applyTerraformMainWriteFailed() throws InterruptedException {
		applyTerraformIOE(newResource(Mockito.mock(Terraforming.class), (s, f) -> new File("random-place/random-place"), false));
	}

	private void applyTerraformIOE(final TerraformResource resource) throws InterruptedException {
		try {
			applyTerraform(resource);
			Assert.fail("Expected IOException");
		} catch (IOException ioe) {
			// Nice, as expected, but more check to do
			Assert.assertTrue(resource.resource.getTask(subscription).isFinished());
			Assert.assertTrue(resource.resource.getTask(subscription).isFailed());
		}
	}

	@Test
	public void applyTerraformLogWriteFailed() throws InterruptedException {
		applyTerraformIOE(newResource(Mockito.mock(Terraforming.class), (s, f) -> f.length > 0 && f[0].equals("main.log")
				? new File("random-place/random-place") : f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]), false));
	}

	@Test
	public void applyTerraformExit0() throws Exception {
		applyTerraformExit(0, "Terraform exit code 0 -> no need to continue");
	}

	@Test(expected = BusinessException.class)
	public void applyTerraformExit1() throws Exception {
		applyTerraformExit(1, "Terraform exit code 1 -> aborted");
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

		Assert.assertTrue(tf.exists());
		Assert.assertTrue(log.exists());
		final String logString = IOUtils.toString(log.toURI(), "UTF-8");
		Assert.assertTrue(logString.contains("error=" + code));
		Assert.assertTrue(logString.contains(message));
		Assert.assertTrue(resource.resource.getTask(subscription).isFinished());

		if (code == 1) {
			Assert.assertTrue(resource.resource.getTask(subscription).isFailed());
		} else {
			Assert.assertFalse(resource.resource.getTask(subscription).isFailed());
		}
		if (thrown != null) {
			throw thrown;
		}
	}

	@Test
	public void getTerraformLog() throws IOException {
		Assert.assertEquals(404, newResource(Mockito.mock(Terraforming.class)).getTerraformLog(subscription).getStatus());
	}

	@Test
	public void newBuilder() {
		resource.newBuilder().command().contains("terraform");
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
			Assert.assertSame(file, resource.toFile(entity, "some"));
			Assert.assertNotNull(PluginsClassLoader.getInstance());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	private TerraformResource newResource(final Terraforming providerResource, final String... customArgs) {
		return newResource(providerResource, true, customArgs);
	}

	private TerraformResource newResource(final Terraforming providerResource, final boolean dryRun, final String... customArgs) {
		return newResource(providerResource, (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]), dryRun, customArgs);
	}

	private TerraformResource newResource(final Terraforming providerResource, final BiFunction<Subscription, String[], File> toFile,
			final boolean dryRun, final String... customArgs) {
		final TerraformResource resource = new TerraformResource() {
			@Override
			protected File toFile(final Subscription subscription, final String file) throws IOException {
				return toFile.apply(subscription, new String[] { file });
			}

			@Override
			protected ProcessBuilder newBuilder(String... args) {
				return new ProcessBuilder(
						ArrayUtils.addAll(new String[] { "java", "-cp", MOCK_PATH.getParent(), "org.ligoj.app.plugin.prov.Main" },
								customArgs.length > 0 ? customArgs : args));
			}

			/**
			 * Prepare the Terraform environment to apply the new environment.
			 * Note there is no concurrency check.
			 */
			@Override
			protected File applyTerraform(final Subscription entity, final Terraforming terra, final QuoteVo configuration)
					throws IOException, InterruptedException {
				if (dryRun) {
					// Ignore this call
					return null;
				}
				return super.applyTerraform(entity, terra, configuration);
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Mock to disable inner transactions for this test
		resource.resource = new ProvResource() {
			@Override
			public TerraformStatus startTask(final int subscription) {
				return super.startTask(subscription);
			}

			@Override
			public void endTask(final int subscription, final boolean failed) {
				super.endTask(subscription, failed);
			}

			@Override
			public void nextStep(final TerraformStatus task) {
				super.nextStep(task);
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.resource);

		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);

		// Replace the plugin locator
		resource.locator = locator;
		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class)).thenReturn(providerResource);
		return resource;
	}
}
