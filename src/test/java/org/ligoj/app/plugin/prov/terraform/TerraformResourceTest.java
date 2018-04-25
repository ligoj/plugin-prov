package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.awaitility.Awaitility;
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
import org.ligoj.bootstrap.model.system.SystemConfiguration;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
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
	private static final File TEST_LOGS = new File("target/test-classes/terraform-logs").getAbsoluteFile();

	private static final File MOCK_PATH = new File("target/test-classes/terraform-it").getAbsoluteFile();

	private int subscription;

	@Autowired
	protected ConfigurationResource configuration;

	@Autowired
	private TerraformRunnerResource runner;
	@Autowired
	private TerraformUtils utils;

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
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class,
						ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class, SystemConfiguration.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		cacheManager.getCache("terraform-version-latest").clear();
		cacheManager.getCache("terraform-version").clear();
	}

	@Test
	public void generateAndExecuteNotSupported() {
		Assertions.assertEquals("terraform-no-supported", Assertions.assertThrows(BusinessException.class, () -> {
			newResource(null).generateAndExecute(subscription);
		}).getMessage());
	}

	@Test
	public void download() throws IOException {
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		final File target = new File("target/test-classes/terraform-zip");
		FileUtils.deleteDirectory(target);
		FileUtils.forceMkdir(target);
		final File zipFile = new File(target, "download.zip");
		FileUtils.deleteQuietly(zipFile);
		final TerraformResource resource = newResource(terraforming);

		// Write some files : logs and secrets
		writeOldFiles();
		FileUtils.write(new File(MOCK_PATH, ".terraform/any.log"), "any", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "plan.ptf"), "plan", StandardCharsets.UTF_8);
		FileUtils.forceMkdir(new File(MOCK_PATH, "module"));
		FileUtils.write(new File(MOCK_PATH, "module/some.tf"), "module.", StandardCharsets.UTF_8);

		final FileOutputStream zipOut = new FileOutputStream(zipFile);
		((StreamingOutput) resource.download(subscription, "sample.zip").getEntity()).write(zipOut);
		zipOut.close();

		// Unzip and check the files
		final List<File> files = resource.utils.unzip(target, new FileInputStream(zipFile));
		Assertions.assertTrue(files.stream().noneMatch(f -> f.getName().endsWith(".ptf")));
		Assertions.assertTrue(files.stream().noneMatch(f -> f.getName().contains(".terraform")));
		Assertions.assertTrue(files.stream().noneMatch(f -> f.getName().contains("secret")));
		Assertions.assertFalse((new File(target, "plan.ptf").exists()));
		Assertions.assertFalse((new File(target, "secrets.auto.tfvars").exists()));
		Assertions.assertEquals("old-main.",
				FileUtils.readFileToString(new File(target, "main.tf"), StandardCharsets.UTF_8));
		Assertions.assertEquals("old-init.",
				FileUtils.readFileToString(new File(target, "init.log"), StandardCharsets.UTF_8));
		Assertions.assertEquals("module.",
				FileUtils.readFileToString(new File(target, "module/some.tf"), StandardCharsets.UTF_8));

	}

	/**
	 * {@link IOException} during the asynchronous execution
	 */
	@Test
	public void generateAndExecuteError() {
		final TerraformResource resource = new TerraformResource() {

			@Override
			protected void generateAndExecute(final Subscription entity, final Terraforming terra,
					final QuoteVo configuration, final List<String[]> sequence) throws IOException {
				throw new IOException();
			}
		};
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Mock to disable inner transactions for this test
		resource.resource = Mockito.mock(ProvResource.class);
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);

		// Replace the plugin locator
		resource.locator = locator;
		resource.runner = new TerraformRunnerResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.runner);
		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class))
				.thenReturn(Mockito.mock(Terraforming.class));
		resource.generateAndExecute(subscription);
	}

	@Test
	public void generateAndExecute() {
		final TerraformStatus status = newResource(Mockito.mock(Terraforming.class)).generateAndExecute(subscription);
		Assertions.assertEquals(subscription, status.getSubscription());
	}

	private Subscription getSubscription() {
		return em.find(Subscription.class, subscription);
	}

	@Test
	public void getTerraformLogEmpty() throws IOException {
		final TerraformResource resource = newResource(newTerraforming(), false);
		// Check the log file is well handled
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
		final String string = bos.toString(StandardCharsets.UTF_8);
		Assertions.assertEquals("", string);
	}

	@Test
	public void getTerraformLog() throws IOException {
		// Cleanup
		FileUtils.listFiles(MOCK_PATH, new String[] { ".log" }, true);
		writeOldFiles();
		final TerraformResource resource = newResource(newTerraforming(), false);
		// Check the log file is well handled
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
		final String string = bos.toString(StandardCharsets.UTF_8);
		Assertions.assertEquals("old-init.old-plan.old-show.old-apply.", string);
	}

	private void generateAndExecute(final TerraformResource resource, final Terraforming terraforming,
			final QuoteVo quote) throws IOException, InterruptedException {
		final List<String[]> sequence = utils.getTerraformCommands();
		resource.startTask(getSubscription(), sequence);
		resource.generateAndExecute(getSubscription(), terraforming, quote, sequence);
	}

	@Test
	public void generateAndExecuteInternal() throws IOException, InterruptedException {
		final File log = new File(MOCK_PATH, "apply.log");
		final File tf = new File(MOCK_PATH, "main.tf");
		final Terraforming terraforming = newTerraforming();
		final TerraformResource resource = newResource(terraforming, false);
		writeOldFiles();
		generateAndExecute(resource, terraforming, null);

		// Synchronization of sub processes
		Awaitility.await().atMost(5, TimeUnit.SECONDS)
				.until(() -> tf.exists() && log.exists() && IOUtils.toString(log.toURI(), "UTF-8").contains("apply"));
		Thread.yield();

		Assertions.assertTrue(tf.exists());
		Assertions.assertTrue(log.exists());
		Assertions.assertTrue(IOUtils.toString(new File(MOCK_PATH, "init.log").toURI(), "UTF-8").contains("init"));
		Assertions.assertTrue(IOUtils.toString(new File(MOCK_PATH, "plan.log").toURI(), "UTF-8").contains("plan"));
		Assertions.assertTrue(IOUtils.toString(new File(MOCK_PATH, "show.log").toURI(), "UTF-8").contains("show"));
		Assertions.assertTrue(IOUtils.toString(new File(MOCK_PATH, "apply.log").toURI(), "UTF-8").contains("apply"));

		// Check the log file is well handled
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
		final String string = bos.toString(StandardCharsets.UTF_8);
		Assertions.assertTrue(string.contains("init"));
		Assertions.assertTrue(string.contains("plan"));
		Assertions.assertTrue(string.contains("show"));
		Assertions.assertTrue(string.contains("apply"));

		// Check the task status
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertTrue(task.isFinished());
		Assertions.assertFalse(task.isFailed());
		Assertions.assertNotNull(task.getStart());
		Assertions.assertNotNull(task.getAuthor());
		Assertions.assertEquals(DEFAULT_USER, task.getAuthor());
		Assertions.assertEquals("service:prov:test:account", task.getLocked().getId());
		Assertions.assertNotNull(task.getEnd());
		Assertions.assertEquals("init,plan,show,apply", task.getSequence());
		Assertions.assertEquals("apply", task.getSequence().split(",")[task.getCommandIndex()]);
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getProcessing());
		Assertions.assertEquals(0, task.getAdded());
		Assertions.assertEquals(0, task.getDeleted());
		Assertions.assertEquals(0, task.getUpdated());
		Assertions.assertTrue(task.isFinished());
		Assertions.assertTrue(task.isFinished());
	}

	private void writeOldFiles() throws IOException {
		FileUtils.write(new File(MOCK_PATH, "init.log"), "old-init.", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "plan.log"), "old-plan.", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "show.log"), "old-show.", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "apply.log"), "old-apply.", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "main.tf"), "old-main.", StandardCharsets.UTF_8);
		FileUtils.write(new File(MOCK_PATH, "secrets.auto.tfvars"), "old-secrets..", StandardCharsets.UTF_8);
	}

	private Terraforming newTerraforming() throws IOException {
		final File tf = new File(MOCK_PATH, "main.tf");
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		Mockito.doAnswer(i -> {
			FileUtils.touch(tf);
			return null;
		}).when(terraforming).generate(Mockito.any(), Mockito.any());
		return terraforming;
	}

	@Test
	private void generateAndExecuteIOE() throws IOException {
		final Terraforming terraforming = Mockito.mock(Terraforming.class);
		Mockito.doThrow(new IOException()).when(terraforming).generate(Mockito.any(), Mockito.any());

		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class),
				(s, f) -> new File("random-place/random-place"), false);
		Assertions.assertThrows(IOException.class, () -> {
			generateAndExecute(resource, terraforming, null);
		});
		// Nice, as expected, but more check to do
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertNotNull(task);
		Assertions.assertTrue(task.isFinished());
		Assertions.assertTrue(resource.runner.getTask("service:prov:test:account").isFailed());
	}

	@Test
	public void generateAndExecuteExit0() throws Exception {
		generateAndExecuteExit(0, "Terraform exit code 0 -> no need to continue");
	}

	@Test
	public void getVersion() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0",
				"Terraform v0.0.1");
		final TerraformInformation version = resource.getVersion();
		Assertions.assertEquals("0.0.1", version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionNotLatest() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0",
				"Terraform v0.0.1\n\nYour version of Terraform is out of date! The latest version\n"
						+ "is 0.11.7. You can update by downloading from www.terraform.io/downloads.html");
		final TerraformInformation version = resource.getVersion();
		Assertions.assertEquals("0.0.1", version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionWrongOutput() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0", "WHAT?");
		final TerraformInformation version = resource.getVersion();
		Assertions.assertNull(version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionNotInstalled() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0", "WHAT?");

		// Replace the CLI runner
		resource.utils = new TerraformUtils() {

			@Override
			public boolean isInstalled() {
				return false;
			}

			@Override
			public String getLastestVersion() {
				return "2.0.0";
			}
		};
		final TerraformInformation version = resource.getVersion();
		Assertions.assertNull(version.getVersion());
		Assertions.assertFalse(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void install() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=0",
				"Terraform v2.0.1");
		final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(MOCK_PATH.toPath());

		// Replace the Terraform utility
		resource.utils = new TerraformUtils() {

			@Override
			public void install(final String version) {
				Assertions.assertEquals("2.0.0", version);
			}

			@Override
			protected PluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			public String getLastestVersion() {
				return "2.0.0";
			}
		};
		final TerraformInformation version = resource.install("2.0.0");
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionExit1() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=1");
		final TerraformInformation version = resource.getVersion();
		Assertions.assertNull(version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void executeExit1() {
		Assertions.assertEquals("aborted", Assertions.assertThrows(BusinessException.class, () -> {
			generateAndExecuteExit(1, "Terraform exit code 1 -> aborted");
		}).getMessage());
	}

	private void generateAndExecuteExit(final int code, final String message) throws Exception {
		final File log = new File(MOCK_PATH, "init.log");
		final File tf = new File(MOCK_PATH, "main.tf");
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), false, "error=" + code);
		Exception thrown = null;
		try {
			generateAndExecute(resource, newTerraforming(), null);
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

			/**
			 * Prepare the Terraform environment to apply the new environment. Note there is no concurrency check.
			 */
			@Override
			protected void generateAndExecute(final Subscription entity, final Terraforming terra,
					final QuoteVo configuration, final List<String[]> sequence)
					throws IOException, InterruptedException {
				if (dryRun) {
					// Ignore this call
					return;
				}
				super.generateAndExecute(entity, terra, configuration, sequence);
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
		resource.utils = new TerraformUtils() {

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
			protected PluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			public String getLastestVersion() {
				return "2.0.0";
			}

			@Override
			public File toFile(final Subscription subscription, final String... file) {
				return toFile.apply(subscription, file);
			}
		};
		resource.utils.configuration = configuration;
		resource.runner.utils = resource.utils;
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

	@Test
	public void getTaskNoRight() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		super.initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> runner.getTask(getSubscription().getId()));
	}

	@Test
	public void getTaskNoLog() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getProcessing());
	}

	@Test
	public void getTask() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		Files.copy(TEST_LOGS.toPath().resolve("apply.log"), new File(MOCK_PATH, "apply.log").toPath());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(44, task.getCompleted());
		Assertions.assertEquals(0, task.getProcessing());
	}

	@Test
	public void getTaskUnfinishedTasks() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		Files.copy(TEST_LOGS.toPath().resolve("apply-not-completed.log"), new File(MOCK_PATH, "apply.log").toPath());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(1, task.getCompleted());
		Assertions.assertEquals(1, task.getProcessing());
	}

	@Test
	public void getTaskLockedDifferentSubscription() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, -1);
		Assertions.assertEquals("concurrent-terraform-account", Assertions
				.assertThrows(BusinessException.class, () -> runner.getTask(getSubscription().getId())).getMessage());
	}

	@Test
	public void computeWorkload() throws IOException {
		Files.copy(TEST_LOGS.toPath().resolve("show.log"), new File(MOCK_PATH, "show.log").toPath());
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		resource.computeWorkload(getSubscription());
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getProcessing());
		Assertions.assertEquals(3, task.getAdded());
		Assertions.assertEquals(2, task.getUpdated());
		Assertions.assertEquals(1, task.getDeleted());
	}

	private void startTask(final TerraformResource resource, final int subscription) {
		resource.runner.startTask("service:prov:test:account", t -> {
			t.setAdded(0);
			t.setDeleted(0);
			t.setUpdated(0);
			t.setProcessing(0);
			t.setCompleted(0);
			t.setSubscription(subscription);
		});
	}
}
