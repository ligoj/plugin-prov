package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
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
public class TerraformResourceTest extends AbstractTerraformTest {

	/**
	 * Coverage only code for interface and beans only used by implementors.
	 */
	@Test
	public void coverage() {
		InstanceMode.values()[InstanceMode.AUTO_SCALING.ordinal()].name();
		final Context context = new Context();
		context.setInstances(context.getInstances());
		context.setLocation(context.getLocation());
		context.setModes(context.getModes());
		context.setContext(context.getContext());
		context.setQuote(context.getQuote());
	}

	@Test
	public void createNotSupported() {
		Assertions.assertEquals("terraform-no-supported", Assertions
				.assertThrows(BusinessException.class, () -> newResource(null).create(subscription, new Context()))
				.getMessage());
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

		try (final FileOutputStream zipOut = new FileOutputStream(zipFile)) {
			((StreamingOutput) resource.download(subscription, "sample.zip").getEntity()).write(zipOut);
		}

		// Unzip and check the files
		final List<File> files = resource.utils.unzip(new FileInputStream(zipFile), target);
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
	public void sequenceError() {
		final TerraformResource resource = new TerraformResource() {

			@Override
			public void sequenceNewTransaction(final Context context) throws IOException {
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
		final Context context = new Context();
		context.add("key", "  value  ");
		resource.create(subscription, context);
		Assertions.assertEquals("value", context.get("key"));
	}

	@Test
	public void getLogEmpty() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
			Assertions.assertEquals("", bos.toString(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void getLogNoTask() throws IOException {
		writeOldFiles();
		final TerraformResource resource = newResource(newTerraforming());
		// Check the log file is well handled
		try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
			Assertions.assertEquals("", bos.toString(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void getLog() throws IOException {
		writeOldFiles();
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription, "init,plan,show");
		// Check the log file is well handled
		try (final ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
			((StreamingOutput) resource.getLog(subscription).getEntity()).write(bos);
			Assertions.assertEquals("---- init ----\nold-init.\n---- plan ----\nold-plan.\n---- show ----\nold-show.\n",
					bos.toString(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void clean() throws IOException, InterruptedException {
		final File tf = new File(MOCK_PATH, "main.tf");
		final File terraformDir = new File(MOCK_PATH, ".terraform");
		FileUtils.forceMkdir(terraformDir);
		addGeneratedFiles();

		final Context context = new Context();
		context.setSubscription(getSubscription());
		mockResource(newTerraforming(), (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]),
				new TerraformResource()).getAction("clean").execute(context, null);

		Assertions.assertFalse(tf.exists());
		Assertions.assertTrue(terraformDir.exists());
		Assertions.assertFalse(new File(MOCK_PATH, "bar.tf").exists());
		Assertions.assertFalse(new File(MOCK_PATH, "any.ext").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "any.tfstate").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "terraform.tfstate.backup").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "some.keep.tf").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "sub-dir/other.keep.tf").exists());
		Assertions.assertTrue(new File(MOCK_PATH, ".terraform/foo.tf").exists());
	}

	@Test
	public void secrets() throws IOException, InterruptedException {
		final Context context = new Context();
		context.setSubscription(getSubscription());
		mockResource(newTerraforming(), (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]),
				new TerraformResource()).getAction("secrets").execute(context, null);
	}

	@Test
	public void generate() throws IOException, InterruptedException {
		final Context context = new Context();
		context.setSubscription(getSubscription());
		mockResource(newTerraforming(), (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]),
				new TerraformResource()).getAction("generate").execute(context, null);
	}

	@Test
	public void create() throws IOException, InterruptedException {
		final File log = new File(MOCK_PATH, "apply.log");
		final TerraformResource resource = mockResource(newTerraforming(), (s, f) -> null, new TerraformResource() {

			@Override
			public void sequenceNewTransaction(final Context context) throws IOException {
				FileUtils.touch(log);
			}
		});

		// Create
		final List<String[]> sequence = utils.getTerraformCommands(TerraformSequence.CREATE);
		final Context context = new Context();
		context.setSubscription(getSubscription());
		context.setSequence(sequence);
		resource.create(subscription, context);

		// Synchronization of sub processes
		Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> log.exists());
		Thread.yield();

		// Check the task status
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertFalse(task.isFinished());
		Assertions.assertFalse(task.isFailed());
		Assertions.assertNotNull(task.getStart());
		Assertions.assertNotNull(task.getAuthor());
		Assertions.assertEquals(DEFAULT_USER, task.getAuthor());
		Assertions.assertEquals("service:prov:test:account", task.getLocked().getId());
		Assertions.assertNull(task.getEnd());
		Assertions.assertEquals("clean,generate,init,secrets,plan,show,apply", task.getSequence());
		Assertions.assertNull(task.getCommandIndex());
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
		Assertions.assertEquals(0, task.getToAdd());
		Assertions.assertEquals(0, task.getToDestroy());
		Assertions.assertEquals(0, task.getToReplace());
		Assertions.assertEquals(0, task.getToUpdate());
		Assertions.assertEquals(TerraformSequence.CREATE, task.getType());
	}

	@Test
	public void destroy() {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class));
		final TerraformStatus status = resource.destroy(subscription, new Context());
		Assertions.assertEquals(subscription, status.getSubscription());
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(TerraformSequence.DESTROY, task.getType());
	}

	@Test
	public void sequenceNewTransactionIOE() throws IOException, InterruptedException {
		final TerraformResource resource = new TerraformResource();
		mockResource(newTerraforming(), (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]), resource);
		Mockito.doThrow(new IOException()).when(resource.executableCommand).execute(ArgumentMatchers.any(Context.class),
				ArgumentMatchers.any(), ArgumentMatchers.any());
		final Context context = new Context();
		context.setSubscription(getSubscription());
		context.setSequence(Collections.singletonList(new String[] { "any" }));
		startTask(resource, subscription);
		Assertions.assertThrows(IOException.class, () -> resource.sequenceNewTransaction(context));
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertNotNull(task);
		Assertions.assertTrue(task.isFinished());
		Assertions.assertTrue(resource.runner.getTask("service:prov:test:account").isFailed());
	}

	@Test
	public void sequenceNewTransaction() throws IOException, InterruptedException {
		final Terraforming terraforming = newTerraforming();
		final File log = new File(MOCK_PATH, "apply.log");
		final TerraformResource resource = newResource(terraforming);
		addGeneratedFiles();

		// Create
		final List<String[]> sequence = utils.getTerraformCommands(TerraformSequence.CREATE);
		final Context context = new Context();
		context.setSubscription(getSubscription());
		context.setSequence(sequence);
		startTask(resource, subscription, "clean,generate,secrets,init,plan,show,apply");
		resource.sequenceNewTransaction(context);

		// Synchronization of sub processes
		Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> log.exists());
		Thread.yield();

		Assertions.assertTrue(new File(MOCK_PATH, "clean.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "secrets.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "generate.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "plan.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "init.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "show.log").exists());
		Assertions.assertTrue(new File(MOCK_PATH, "apply.log").exists());

		// Check the task status
		final TerraformStatus task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertTrue(task.isFinished());
		Assertions.assertFalse(task.isFailed());
		Assertions.assertNotNull(task.getStart());
		Assertions.assertNotNull(task.getAuthor());
		Assertions.assertEquals(DEFAULT_USER, task.getAuthor());
		Assertions.assertEquals("service:prov:test:account", task.getLocked().getId());
		Assertions.assertNotNull(task.getEnd());
		Assertions.assertEquals("clean,generate,secrets,init,plan,show,apply", task.getSequence());
		Assertions.assertEquals("apply", task.getSequence().split(",")[task.getCommandIndex()]);
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
		Assertions.assertEquals(0, task.getToAdd());
		Assertions.assertEquals(0, task.getToDestroy());
		Assertions.assertEquals(0, task.getToReplace());
		Assertions.assertEquals(0, task.getToUpdate());
		Assertions.assertTrue(task.isFinished());
	}

	private void addGeneratedFiles() throws IOException {
		FileUtils.touch(new File(MOCK_PATH, "any.tfstate"));
		FileUtils.touch(new File(MOCK_PATH, "terraform.tfstate.backup"));
		FileUtils.touch(new File(MOCK_PATH, "some.keep.tf"));
		FileUtils.touch(new File(MOCK_PATH, "sub-dir/other.keep.tf"));
		FileUtils.touch(new File(MOCK_PATH, ".terraform/foo.tf"));
		FileUtils.touch(new File(MOCK_PATH, "bar.tf"));
		FileUtils.touch(new File(MOCK_PATH, "any.ext"));
		writeOldFiles();
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
		}).when(terraforming).generate(Mockito.any());
		return terraforming;
	}

	@Test
	public void getVersion() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=0");
		Mockito.doAnswer(i -> {
			((OutputStream) i.getArgument(1)).write("Terraform v0.0.1\nAny".getBytes());
			return 0;
		}).when(resource.executableCommand).execute(ArgumentMatchers.any(File.class), ArgumentMatchers.any(),
				ArgumentMatchers.any());
		final TerraformInformation version = resource.getVersion();
		Assertions.assertEquals("0.0.1", version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionNotLatest() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=0");
		resource.executableCommand = Mockito.mock(TerraformBaseCommand.class);
		Mockito.doAnswer(i -> {
			((OutputStream) i.getArgument(1))
					.write(("Terraform v0.0.1\n\nYour version of Terraform is out of date! The latest version\n"
							+ "is 0.11.7. You can update by downloading from www.terraform.io/downloads.html")
									.getBytes());
			return 0;
		}).when(resource.executableCommand).execute(ArgumentMatchers.any(File.class), ArgumentMatchers.any(),
				ArgumentMatchers.any());
		final TerraformInformation version = resource.getVersion();
		Assertions.assertEquals("0.0.1", version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionWrongOutput() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=0", "WHAT?");
		resource.executableCommand = Mockito.mock(TerraformBaseCommand.class);
		Mockito.doAnswer(i -> {
			((OutputStream) i.getArgument(1)).write("not_a_version".getBytes());
			return null;
		}).when(resource.executableCommand).execute(ArgumentMatchers.any(File.class), ArgumentMatchers.any(),
				ArgumentMatchers.any());
		final TerraformInformation version = resource.getVersion();
		Assertions.assertNull(version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionNotInstalled() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=0");

		// Replace the CLI runner
		resource.utils = new TerraformUtils() {

			@Override
			public boolean isInstalled() {
				return false;
			}

			@Override
			public String getLatestVersion() {
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
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=0", "Terraform v2.0.1");
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
			public String getLatestVersion() {
				return "2.0.0";
			}
		};
		final TerraformInformation version = resource.install("2.0.0");
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	@Test
	public void getVersionExit1() throws Exception {
		final TerraformResource resource = newResource(Mockito.mock(Terraforming.class), "error=1");
		resource.executableCommand = Mockito.mock(TerraformBaseCommand.class);
		Mockito.doReturn(1).when(resource.executableCommand).execute(ArgumentMatchers.any(File.class),
				ArgumentMatchers.any(), ArgumentMatchers.any());
		final TerraformInformation version = resource.getVersion();
		Assertions.assertNull(version.getVersion());
		Assertions.assertTrue(version.isInstalled());
		Assertions.assertEquals("2.0.0", version.getLastVersion());
	}

	private TerraformResource newResource(final Terraforming providerResource, final String... customArgs) {
		return newResource(providerResource, (s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]),
				customArgs);
	}

	private TerraformResource newResource(final Terraforming providerResource,
			final BiFunction<Subscription, String[], File> toFile, final String... customArgs) {
		final TerraformAction action = Mockito.mock(TerraformAction.class);
		final TerraformResource resource = new TerraformResource() {

			@Override
			protected TerraformAction getAction(final String command) {
				return action;
			}
		};
		return mockResource(providerResource, toFile, resource, customArgs);
	}

	private TerraformResource mockResource(final Terraforming providerResource,
			final BiFunction<Subscription, String[], File> toFile, final TerraformResource resource,
			final String... customArgs) {
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		resource.self = resource;
		resource.resource = new ProvResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.resource);

		// Replace the plugin locator
		final ServicePluginLocator locator = Mockito.mock(ServicePluginLocator.class);
		resource.locator = locator;

		// Replace the runner
		resource.runner = new TerraformRunnerResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.runner);

		Mockito.when(locator.getResource("service:prov:test:account", Terraforming.class)).thenReturn(providerResource);

		// Replace the CLI runner
		resource.utils = newTerraformUtils(toFile, customArgs);
		resource.runner.utils = resource.utils;
		resource.executableCommand = Mockito.mock(TerraformBaseCommand.class);
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
		Assertions.assertEquals(0, task.getCompleting());
	}

	@Test
	public void getTask() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		Files.copy(TEST_LOGS.toPath().resolve("apply.log"), new File(MOCK_PATH, "apply.log").toPath());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(44, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
	}

	@Test
	public void getTaskUnfinishedTasks() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		Files.copy(TEST_LOGS.toPath().resolve("apply-not-completed.log"), new File(MOCK_PATH, "apply.log").toPath());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(3, task.getCompleted());
		Assertions.assertEquals(1, task.getCompleting());
	}

	@Test
	public void getTaskUnfinishedStateTasks() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		Files.copy(TEST_LOGS.toPath().resolve("destroy-not-completed.log"),
				new File(MOCK_PATH, "destroy.log").toPath());
		startTask(resource, subscription, "destroy");
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(3, task.getCompleted());
		Assertions.assertEquals(1, task.getCompleting());
	}

	@Test
	public void getTaskNotYetApply() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
	}

	@Test
	public void getTaskError() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, subscription);
		resource.runner.utils = Mockito.mock(TerraformUtils.class);
		Mockito.doThrow(new IOException()).when(resource.runner.utils).toFile(Mockito.any(), Mockito.any());
		final TerraformStatus task = resource.runner.getTask(getSubscription().getId());
		Assertions.assertEquals(subscription, task.getSubscription());
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
	}

	@Test
	public void getTaskLockedDifferentSubscription() throws IOException {
		final TerraformResource resource = newResource(newTerraforming());
		startTask(resource, -1);
		Assertions.assertNull(runner.getTask(getSubscription().getId()));
	}

	@Test
	public void getTaskNotStarted() throws IOException {
		Assertions.assertNull(runner.getTask(getSubscription().getId()));
	}

	private void startTask(final TerraformResource resource, final int subscription) {
		startTask(resource, subscription, "apply");
	}

	private void startTask(final TerraformResource resource, final int subscription, final String sequence) {
		resource.runner.startTask("service:prov:test:account", t -> {
			t.setToAdd(0);
			t.setToDestroy(0);
			t.setToUpdate(0);
			t.setToReplace(0);
			t.setCompleting(0);
			t.setCompleted(0);
			t.setSubscription(subscription);
			t.setSequence(sequence);
		});
	}
}
