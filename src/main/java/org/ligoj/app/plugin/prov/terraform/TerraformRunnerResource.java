package org.ligoj.app.plugin.prov.terraform;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

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

	private static final Pattern PATTERN = Pattern.compile("^[^\\s:]+: (([A-Za-z]+)...|([A-Za-z]+) complete after)");

	private static final Set<String> COMPLETED_OPERATIONS = new HashSet<>(
			Arrays.asList("Destruction", "Creation", "Modifications"));

	private static final Set<String> PENDING_OPERATIONS = new HashSet<>(
			Arrays.asList("Creating", "Modifying", "Destroying"));

	/**
	 * Return the Terraform status from the given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier.
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
	 * @param subscription
	 *            The subscription entity.
	 * @return The Terraform status from the given subscription identifier. <code>null</code> when the is no task
	 *         associated to this subscription.
	 */
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public TerraformStatus getTaskInternal(final Subscription subscription) {
		final TerraformStatus status = LongTaskRunnerNode.super.getTask(subscription.getNode().getId());
		if (status == null || status.getSubscription() != subscription.getId()) {
			// Subscription is valid but not related to the current task
			// Another subscription is running on this node
			return null;
		}

		completeProgress(subscription, status);
		return status;
	}

	/**
	 * Update the given status with the actual progress of appliance. Is based on the "apply.log" file when present.
	 * 
	 * @param subscription
	 *            subscription requesting the task.
	 * @param status
	 *            The status to update.
	 */
	private void completeProgress(final Subscription subscription, final TerraformStatus status) {
		final AtomicInteger creating = new AtomicInteger();
		final AtomicInteger created = new AtomicInteger();
		try (Stream<String> stream = utils.toFile(subscription, "apply.log").exists()
				? Files.lines(utils.toFile(subscription, "apply.log").toPath())
				: Arrays.stream(new String[0])) {
			stream.map(PATTERN::matcher).filter(Matcher::find).forEach(matcher -> {
				if (PENDING_OPERATIONS.contains(matcher.group(2))) {
					creating.incrementAndGet();
				} else if (COMPLETED_OPERATIONS.contains(matcher.group(2))) {
					creating.decrementAndGet();
					created.incrementAndGet();
				}
			});
		} catch (final Exception e) {
			log.warn("Unable to read log file", e);
		}
		status.setProcessing(creating.get());
		status.setCompleted(created.get());
	}

}
