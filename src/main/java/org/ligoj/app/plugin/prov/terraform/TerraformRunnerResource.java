package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;
import java.nio.file.Files;
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
import org.ligoj.bootstrap.core.resource.BusinessException;
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

	private static final Pattern PATTERN = Pattern.compile("^[^\\s:]+: (Creating...|Creation complete after)");

	/**
	 * Return the Terraform status from the given subscription identifier.
	 * 
	 * @param subscription
	 *            The subscription identifier.
	 * @return The Terraform status from the given subscription identifier.
	 */
	@GET
	@Path("{subscription:\\d+}/terraform")
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public TerraformStatus getTask(@PathParam("subscription") final int subscription) {
		final Subscription entity = getSubscriptionResource().checkVisible(subscription);
		TerraformStatus status = LongTaskRunnerNode.super.getTask(entity.getNode().getId());
		if (status.getSubscription() != subscription) {
			// Subscription is valid but not related to the current task
			// Another subscription is running on this node
			throw new BusinessException("concurrent-terraform-account", entity.getNode().getId());
		}

		completeProgress(entity, status);
		return status;
	}

	/**
	 * Update the given status with the actual progress of appliance.
	 * 
	 * @param subscription
	 *            subscription requesting the task.
	 * @param status
	 *            The status to update.
	 */
	protected void completeProgress(final Subscription subscription, final TerraformStatus status) {
		final AtomicInteger creating = new AtomicInteger();
		final AtomicInteger created = new AtomicInteger();
		try (Stream<String> stream = Files.lines(utils.toFile(subscription, "apply.log").toPath())) {
			stream.map(PATTERN::matcher).filter(Matcher::find).forEach(matcher -> {
				if (matcher.group(1).equals("Creating...")) {
					creating.incrementAndGet();
				} else {
					creating.decrementAndGet();
					created.incrementAndGet();
				}
			});
		} catch (IOException e) {
			log.warn("Unable to read log file", e);

		}
		status.setProcessing(creating.get());
		status.setCompleted(created.get());
	}

}
