/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * A Terraform action corresponding to a real Terraform command.
 */
@Slf4j
@Component
public class TerraformBaseCommand implements TerraformAction {

	/**
	 * Pattern filtering the row corresponding to a change.
	 */
	private static final Pattern SHOW_CHANGE = Pattern.compile("^\\s*([\\-~+/]+)");

	/**
	 * Pattern filtering the row corresponding to a state entry.
	 */
	private static final Pattern STATE_CHANGE = Pattern.compile("^[^\\s]+$");

	@Autowired
	protected TerraformUtils utils;

	@Autowired
	protected TerraformRunnerResource runner;

	/**
	 * Compute the workload from the plan's log and update the status related to the given subscription.
	 *
	 * @param subscription The subscription related to this operation.
	 */
	protected void computeWorkload(final Subscription subscription) {
		runner.nextStep(subscription.getNode().getId(), t -> computeWorkload(subscription, t));
	}

	/**
	 * Compute the workload from the plan's log.
	 *
	 * @param subscription The subscription related to this operation.
	 * @param status       The target status to update.
	 */
	protected void computeWorkload(final Subscription subscription, final TerraformStatus status) {
		final var added = new AtomicInteger();
		final var deleted = new AtomicInteger();
		final var updated = new AtomicInteger();
		final var replaced = new AtomicInteger();

		// Iterate over each line
		try (var stream = Files.lines(utils.toFile(subscription, "show.log").toPath())) {
			stream.map(SHOW_CHANGE::matcher).filter(Matcher::find).forEach(matcher -> {
				// Detect the type of this change
				final var type = matcher.group(1);
				if ("+".equals(type)) {
					added.incrementAndGet();
				} else if ("-".equals(type)) {
					deleted.incrementAndGet();
				} else if ("-/+".equals(type)) {
					replaced.incrementAndGet();
				} else {
					updated.incrementAndGet();
				}
			});
		} catch (final IOException e) {
			log.warn("Unable to get the full workload from the 'show' command", e);
		}
		// Update the status
		status.setToAdd(added.get());
		status.setToReplace(replaced.get());
		status.setToDestroy(deleted.get());
		status.setToReplace(replaced.get());
		status.setToUpdate(updated.get());
	}

	/**
	 * Compute the workload from the state list's log and update the status related to the given subscription.
	 *
	 * @param subscription The subscription related to this operation.
	 */
	protected void computeWorkloadState(final Subscription subscription) {
		runner.nextStep(subscription.getNode().getId(), t -> computeWorkloadState(subscription, t));
	}

	/**
	 * Compute the workload from the state list's log.
	 *
	 * @param subscription The subscription related to this operation.
	 * @param status       The target status to update.
	 */
	protected void computeWorkloadState(final Subscription subscription, final TerraformStatus status) {
		try (var stream = Files.lines(utils.toFile(subscription, "state.log").toPath())) {
			status.setToDestroy((int) stream.map(STATE_CHANGE::matcher).filter(Matcher::find).count());
		} catch (final IOException e) {
			log.warn("Unable to get the full workload from the 'state' command", e);
		}
	}

	@Override
	public void execute(final Context context, OutputStream out, final String... arguments)
			throws IOException, InterruptedException {
		handleCode(context.getSubscription(), out, execute(context.getSubscription(), out, arguments));

		// Complete the workload as needed
		final var command = arguments[0];
		if ("show".equals(command)) {
			computeWorkload(context.getSubscription());
		} else if ("state".equals(command) && "list".equals(arguments[1])) {
			computeWorkloadState(context.getSubscription());
		}
	}

	/**
	 * Execute the given Terraform command arguments
	 *
	 * @param directory The working directory of Terraform user.
	 * @param out       The target log outputs.
	 * @param arguments The Terraform arguments passed to the executable.
	 * @return the exit value of the process represented by this {@code Process} object. By convention, the value
	 *         {@code 0} indicates normal termination.
	 * @throws InterruptedException When the execution is interrupted.
	 * @throws IOException          When logs cannot be written.
	 */
	public int execute(final File directory, final OutputStream out, final String... arguments)
			throws InterruptedException, IOException {
		FileUtils.forceMkdir(directory);
		final var builder = utils.newBuilder(arguments).redirectErrorStream(true).directory(directory);
		final var process = builder.start();
		process.getInputStream().transferTo(out);

		// Wait and get the code
		final var code = process.waitFor();
		out.flush();
		return code;
	}

	/**
	 * Execute the given Terraform command arguments
	 */
	private int execute(final Subscription subscription, final OutputStream out, final String... command)
			throws InterruptedException, IOException {
		return execute(utils.toFile(subscription), out, command);
	}

	private void handleCode(final Subscription subscription, final OutputStream out, final int code)
			throws IOException {
		if (code == 0) {
			// Nothing wrong, no change, only useless to go further
			log.info("Terraform paused for {} ({}) : {}", subscription.getId(), subscription, code);
			out.write(("Terraform exit code " + code + " -> no need to continue").getBytes());
		} else if (code != 2) {
			// Something goes wrong
			log.error("Terraform failed for {} ({}) : {}", subscription.getId(), subscription, code);
			out.write(("Terraform exit code " + code + " -> aborted").getBytes());
			throw new BusinessException("aborted");
		}
	}

}
