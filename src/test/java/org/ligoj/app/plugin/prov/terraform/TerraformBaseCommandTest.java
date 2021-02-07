/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.BiFunction;

import javax.transaction.Transactional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.mockito.Mockito;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link TerraformBaseCommand}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class TerraformBaseCommandTest extends AbstractTerraformTest {

	@Test
	void executeExit0() throws Exception {
		executeExit(0, "Terraform exit code 0 -> no need to continue");
	}

	@Test
	void executeExit2() throws Exception {
		executeExit(2, "");
	}

	@Test
	void executeShow() throws Exception {
		final var resource = newResource(false, "error=0", "AAA");
		FileUtils.write(new File(MOCK_PATH, "show.log"), "  + module\n", StandardCharsets.UTF_8);
		execute(resource, "show", "-v");
		final var task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(1, task.getToAdd());
	}

	@Test
	void executeStateList() throws Exception {
		final var resource = newResource(false, "error=0", "AAA");
		FileUtils.write(new File(MOCK_PATH, "state.log"), "module\n\nSome Text\n", StandardCharsets.UTF_8);
		execute(resource, "state", "list");
		final var task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(1, task.getToDestroy());
	}

	@Test
	void executeStateNotListFile() throws Exception {
		execute("state", "show");
	}

	@Test
	void executeShowNoFile() throws Exception {
		execute("show", "-v");
	}

	@Test
	void executeStateListNoFile() throws Exception {
		execute("state", "list");
	}

	@Test
	private void execute(final String command, final String arg) throws Exception {
		final var resource = newResource(false, "error=0", "AAA");
		execute(resource, command, arg);
		emptyWorkload(resource.runner.getTask("service:prov:test:account"));
	}

	private void emptyWorkload(final TerraformStatus task) {
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
		Assertions.assertEquals(0, task.getToAdd());
		Assertions.assertEquals(0, task.getToUpdate());
		Assertions.assertEquals(0, task.getToDestroy());
		Assertions.assertEquals(0, task.getToReplace());
	}

	@Test
	void executeExit1() {
		Assertions.assertEquals("aborted",
				Assertions
						.assertThrows(BusinessException.class, () -> executeExit(1, "Terraform exit code 1 -> aborted"))
						.getMessage());
	}

	private String execute(final TerraformBaseCommand resource, String... arguments)
			throws IOException, InterruptedException {
		final var sequence = utils.getTerraformCommands(TerraformSequence.CREATE);
		final var context = new Context();
		context.setSubscription(getSubscription());
		context.setSequence(sequence);
		startTask(resource, getSubscription().getId());
		final var outputStream = new ByteArrayOutputStream();
		resource.execute(context, outputStream, arguments);
		return new String(outputStream.toByteArray());
	}

	private void executeExit(final int code, final String message) throws Exception {
		final var resource = newResource(false, "error=" + code);
		final var logString = execute(resource, "error=" + code);
		Assertions.assertTrue(logString.contains("error=" + code));
		Assertions.assertTrue(logString.contains(message));
	}

	private TerraformBaseCommand newResource(final String... customArgs) {
		return newResource(true, customArgs);
	}

	private TerraformBaseCommand newResource(final boolean dryRun, final String... customArgs) {
		return newResource((s, f) -> f.length == 0 ? MOCK_PATH : new File(MOCK_PATH, f[0]), dryRun, customArgs);
	}

	private TerraformBaseCommand newResource(final BiFunction<Subscription, String[], File> toFile,
			final boolean dryRun, final String... customArgs) {
		final var resource = new TerraformBaseCommand();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the runner
		resource.runner = new TerraformRunnerResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource.runner);

		resource.utils = newTerraformUtils(toFile, customArgs);
		resource.runner.utils = resource.utils;
		return resource;
	}

	@Test
	void computeWorkload() throws IOException {
		Files.copy(TEST_LOGS.toPath().resolve("show.log"), new File(MOCK_PATH, "show.log").toPath());
		final var resource = newResource();
		startTask(resource, subscription);
		resource.computeWorkload(getSubscription());
		final var task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
		Assertions.assertEquals(3, task.getToAdd());
		Assertions.assertEquals(2, task.getToUpdate());
		Assertions.assertEquals(1, task.getToDestroy());
		Assertions.assertEquals(1, task.getToReplace());
	}

	@Test
	void computeWorkloadState() throws IOException {
		Files.copy(TEST_LOGS.toPath().resolve("state-list.log"), new File(MOCK_PATH, "state.log").toPath());
		final var resource = newResource();
		startTask(resource, subscription);
		resource.computeWorkloadState(getSubscription());
		final var task = resource.runner.getTask("service:prov:test:account");
		Assertions.assertEquals(0, task.getCompleted());
		Assertions.assertEquals(0, task.getCompleting());
		Assertions.assertEquals(0, task.getToAdd());
		Assertions.assertEquals(0, task.getToUpdate());
		Assertions.assertEquals(3, task.getToDestroy());
		Assertions.assertEquals(0, task.getToReplace());
	}

	@Test
	void computeWorkloadError() throws IOException {
		final var resource = newResource();
		resource.utils = Mockito.mock(TerraformUtils.class);
		Mockito.doThrow(new IOException()).when(resource.utils).toFile(Mockito.any(), Mockito.any());
		final var status = new TerraformStatus();
		status.setToAdd(1);
		resource.computeWorkload(getSubscription(), status);
		Assertions.assertEquals(0, status.getToAdd());
	}

	private void startTask(final TerraformBaseCommand resource, final int subscription) {
		startTask(resource, subscription, "apply");
	}

	private void startTask(final TerraformBaseCommand resource, final int subscription, final String sequence) {
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
