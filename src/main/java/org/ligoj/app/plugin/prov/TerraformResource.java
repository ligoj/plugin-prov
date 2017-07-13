package org.ligoj.app.plugin.prov;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Terraforming resource
 */
@Slf4j
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class TerraformResource {

	/**
	 * Configuration key for Terraform command path
	 */
	private static final String TERRAFORM_PATH = "terraform.path";

	/**
	 * Terraform base command with argument. The Terraform binary must be in the
	 * PATH.
	 */
	private static final String[] TERRAFORM_COMMAND_WIN = { "cmd.exe", "/c" };
	private static final String[] TERRAFORM_COMMAND_LINUX = { "sh", "-c" };
	private static final String[] TERRAFORM_COMMAND = SystemUtils.IS_OS_WINDOWS ? TERRAFORM_COMMAND_WIN : TERRAFORM_COMMAND_LINUX;

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ProvResource resource;

	@Autowired
	protected ConfigurationResource configuration;

	@Autowired
	protected ServicePluginLocator locator;

	/**
	 * Produce the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @param file
	 *            The target file name.
	 * @return the {@link Response} ready to be consumed.
	 */
	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("{subscription:\\d+}/{file:.*.tf}")
	public Response getTerraform(@PathParam("subscription") final int subscription, @PathParam("file") final String file) {
		final Terraforming terra = getTerraform(subscription);
		return AbstractToolPluginResource.download(o -> terra.terraform(o, subscription, resource.getConfiguration(subscription)), file)
				.build();
	}

	/**
	 * Get the log of the current or last Terraform execution of a given
	 * subscription.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return the streaming {@link Response} with output.
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform.log")
	public Response getTerraformLog(@PathParam("subscription") final int subscription) throws IOException {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);
		final File log = toFile(entity, "main.log");

		// Check there is a log file
		if (log.exists()) {
			final StreamingOutput so = o ->  FileUtils.copyFile(toFile(entity, "main.log"), o);
			return Response.ok().entity(so).build();
		}

		// No log file for now
		return Response.status(Status.NOT_FOUND).build();
	}

	/**
	 * Apply (plan, apply, show) the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return the streaming {@link Response} with output.
	 */
	@POST
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform")
	public void applyTerraform(@PathParam("subscription") final int subscription) {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);
		final QuoteVo configuration = resource.getConfiguration(entity);

		// Check the provider support the Terraform generation

		final Terraforming terra = getTerraform(entity.getNode());
		log.info("Terraform request for {} ({})", subscription, entity);
		final SecurityContext context = SecurityContextHolder.getContext();

		// The Terraform execution will done into another thread
		Executors.newSingleThreadExecutor().submit(() -> {
			// Restore the context
			log.info("Terraform start for {} ({})", entity.getId(), entity);
			SecurityContextHolder.setContext(context);
			try {
				final File file = applyTerraform(entity, terra, configuration);
				log.info("Terraform succeed for {} ({})", entity.getId(), entity);
				return file;
			} catch (final Exception e) {
				// The error is not put in the Terraform logger for security
				log.error("Terraform failed for {}", entity, e);
			}
			return null;
		});
	}

	/**
	 * Prepare the Terraform environment to apply the new environment. Note
	 * there is no concurrency check.
	 */
	protected File applyTerraform(final Subscription entity, final Terraforming terra, final QuoteVo configuration)
			throws IOException, InterruptedException {
		final File logFile = toFile(entity, "main.log");
		final File tfFile = toFile(entity, "main.tf");

		// Clear the previous generated files
		FileUtils.deleteQuietly(logFile);
		FileUtils.deleteQuietly(tfFile);
		FileOutputStream mainTf = null;
		Writer out = null;
		boolean succeed = false;
		try {
			// Generate and persist the main Terraform file.
			// This file is isolated from the other subscription, inside the
			// subscription context path
			mainTf = new FileOutputStream(tfFile);

			// Create a log file, erasing the previous one
			out = new FileWriter(logFile);

			// Write the Terraform configuration in the 'main.tf' file
			terra.terraform(mainTf, entity.getId(), configuration);

			// Execute the Terraform commands
			executeTerraform(entity, out, getTerraformSequence(), terra.commandLineParameters(entity.getId()));
			succeed = true;
		} finally {
			IOUtils.closeQuietly(mainTf);
			IOUtils.closeQuietly(out);
			endTask(entity, succeed);
		}

		return logFile;
	}

	/**
	 * End the related task
	 */
	private void endTask(final Subscription entity, boolean succeed) {
		resource.endTask(entity.getId(), !succeed);
	}

	private Terraforming getTerraform(final Node node) {
		return Optional.ofNullable(locator.getResource(node.getId(), Terraforming.class))
				.orElseThrow(() -> new BusinessException("terraform-no-supported", node.getRefined().getId()));
	}

	protected String[][] getTerraformSequence() {
		return new String[][] { { "plan", "-input=false", "-no-color", "-detailed-exitcode" }, { "apply", "-input=false", "-no-color" },
				{ "show", "-input=false", "-no-color" } };
	}

	/**
	 * Execute the given Terraform commands. Note there is no concurrency check
	 * for now.
	 */
	private void executeTerraform(final Subscription subscription, final Writer out, final String[][] commands,
			final String... additionalParameters) throws InterruptedException, IOException {
		int step = 0;
		final TerraformStatus task = resource.startTask(subscription.getId());
		for (final String[] command : commands) {
			task.setStep(TerraformStep.values()[step]);
			resource.nextStep(task);
			final int code = executeTerraform(subscription, out, ArrayUtils.addAll(command, additionalParameters));
			if (code == 0) {
				// Nothing wrong, no change, only useless to go further
				log.info("Terraform paused for {} ({}) : {}", subscription.getId(), subscription, code);
				out.write("Terraform exit code " + code + " -> no need to continue");
				break;
			}
			if (code != 2) {
				// Something goes wrong
				log.error("Terraform failed for {} ({}) : {}", subscription.getId(), subscription, code);
				out.write("Terraform exit code " + code + " -> aborted");
				throw new BusinessException("aborted");
			}
			// Code is correct, proceed the next command
			out.flush();
			step++;
		}
	}

	/**
	 * Execute the given Terraform command arguments
	 */
	private int executeTerraform(final Subscription subscription, final Writer out, final String[] command)
			throws InterruptedException, IOException {
		final ProcessBuilder builder = newBuilder(command);
		builder.redirectErrorStream(true);
		// TODO Subscription identifier is implicit strating from API 1.0.9
		builder.directory(toFile(subscription, "main.log").getParentFile());
		final Process process = builder.start();
		IOUtils.copy(process.getInputStream(), out, StandardCharsets.UTF_8);
		return process.waitFor();
	}

	/**
	 * A new {@link ProcessBuilder} with the given arguments
	 */
	protected ProcessBuilder newBuilder(String... args) {
		return new ProcessBuilder(ArrayUtils.addAll(TERRAFORM_COMMAND,
				configuration.get(TERRAFORM_PATH, "terraform") + " " + StringUtils.join(ArrayUtils.addAll(args), ' ')));
	}

	/**
	 * Check the subscription is visible, then check the associated provider
	 * support Terraform generation and return the corresponding
	 * {@link Terraforming} instance.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return {@link Terraforming} instance of this subscription. Never
	 *         <code>null</code>.
	 */
	private Terraforming getTerraform(final int subscription) {
		// Check the provider support the Terraform generation
		return getTerraform(subscriptionResource.checkVisibleSubscription(subscription).getNode());
	}

	/**
	 * Return the file reference from the given subscription.
	 */
	protected File toFile(final Subscription subscription, final String file) throws IOException {
		// TODO Subscription identifier is implicit strating from API 1.0.9
		return PluginsClassLoader.getInstance().toFile(subscription, subscription.getId().toString(), file);
	}
}
