/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.TerraformStatusRepository;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.LongTaskRunnerNode;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Terraforming task runner resource.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class TerraformRunnerResource implements LongTaskRunnerNode<TerraformStatus, TerraformStatusRepository> {

	private static final Pattern PATTERN_APPLY = Pattern.compile("^[^\\s:]+: (([A-Za-z]+)\\.|([A-Za-z]+) complete)(.*)");
	private static final Pattern PATTERN_DESTROY = Pattern
			.compile("^(.+: (Destroying)\\.|.+: (Destruction) complete|data\\.[^:]+:+ (Refreshing))");

	private static final Set<String> COMPLETED_OPERATIONS = new HashSet<>(
			Arrays.asList("Destruction", "Creation", "Modifications", "Refreshing"));

	private static final Set<String> PENDING_OPERATIONS = new HashSet<>(
			Arrays.asList("Creating", "Modifying", "Destroying"));

	@Autowired
	@Getter
	protected TerraformStatusRepository taskRepository;

	@Autowired
	@Getter
	private NodeRepository nodeRepository;

	@Autowired
	@Getter
	private NodeResource nodeResource;

	@Autowired
	@Getter
	private SubscriptionResource subscriptionResource;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	protected TerraformUtils utils;

	@Override
	public Supplier<TerraformStatus> newTask() {
		return TerraformStatus::new;
	}

	/**
	 * Return the Terraform status from the given subscription identifier.
	 *
	 * @param subscription The subscription identifier.
	 * @return The Terraform status from the given subscription identifier. <code>null</code> when the is no task
	 *         associated to this subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/terraform")
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public TerraformStatus getTask(@PathParam("subscription") final int subscription) {
		return getTaskInternal(getSubscriptionResource().checkVisible(subscription));
	}

	/**
	 * Return the Terraform status from the given subscription.
	 *
	 * @param subscription The subscription entity.
	 * @return The Terraform status from the given subscription identifier. <code>null</code> when the is no task
	 *         associated to this subscription.
	 */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public TerraformStatus getTaskInternal(final Subscription subscription) {
		final var status = getTaskInternal(subscription.getNode().getId());
		if (status == null || status.getSubscription() != subscription.getId()) {
			// Subscription is valid but not related to the current task
			// Another subscription is running on this node
			return null;
		}

		// Complete with log contents
		if (status.getSequence().contains("apply")) {
			completeProgress(subscription, status, "apply.log", this::parseApplyLogLine);
		}
		if (status.getSequence().contains("destroy")) {
			completeProgress(subscription, status, "destroy.log", this::parseDestroyLogLine);
		}
		return status;
	}

	/**
	 * Update the given status with the actual progress of appliance. Is based on the given log file when present.
	 *
	 * @param subscription subscription requesting the task.
	 * @param status       The status to update.
	 */
	private void completeProgress(final Subscription subscription, final TerraformStatus status, final String file,
			BiConsumer<TerraformStatus, Stream<String>> apply) {
		// Parse each line of the log file
		try (var stream = utils.toFile(subscription, file).exists()
				? Files.lines(utils.toFile(subscription, file).toPath())
				: Arrays.stream(new String[0])) {
			// Parse the line with RegEx
			apply.accept(status, stream);
		} catch (final Exception e) {
			log.warn("Unable to read log file {}", file, e);
		}
	}

	/**
	 * Parse the given apply log stream and update the completing and completed cursors in the given status.
	 *
	 * @param status The status to update.
	 * @param stream The line stream.
	 */
	protected void parseApplyLogLine(final TerraformStatus status, final Stream<String> stream) {
		parseLogLine(status, stream, PATTERN_APPLY);
	}

	/**
	 * Parse the given state log stream and update the completing and completed cursors in the given status.
	 *
	 * @param status The status to update.
	 * @param stream The line stream.
	 */
	protected void parseDestroyLogLine(final TerraformStatus status, final Stream<String> stream) {
		parseLogLine(status, stream, PATTERN_DESTROY);
	}

	/**
	 * Parse the given log stream and update the completing and completed cursors in the given status.
	 *
	 * @param status  The status to update.
	 * @param stream  The line stream.
	 * @param pattern The {@link Pattern} mating each line with capture groups.
	 *                <ul>
	 *                <li>Group 2 corresponds to a pending action</li>
	 *                <li>Group 3 corresponds to a completed action associated to a pending action</li>
	 *                <li>Group 4 corresponds to a completed action</li>
	 *                </ul>
	 */
	private void parseLogLine(final TerraformStatus status, final Stream<String> stream, final Pattern pattern) {
		stream.map(pattern::matcher).filter(Matcher::find).forEach(matcher -> {
			if (COMPLETED_OPERATIONS.contains(matcher.group(3))) {
				status.setCompleting(status.getCompleting() - 1);
				status.setCompleted(status.getCompleted() + 1);
			} else if (COMPLETED_OPERATIONS.contains(matcher.group(4))) {
				status.setCompleted(status.getCompleted() + 1);
			} else if (PENDING_OPERATIONS.contains(matcher.group(2))) {
				status.setCompleting(status.getCompleting() + 1);
			}
		});
	}

}
