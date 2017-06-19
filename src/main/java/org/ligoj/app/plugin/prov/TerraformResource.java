package org.ligoj.app.plugin.prov;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
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

	private static final String[] TERRAFORM_COMMAND_WIN = { "cmd.exe", "/c", "terraform" };
	private static final String[] TERRAFORM_COMMAND_LINUX = { "sh", "-c", "terraform" };

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected ProvResource resource;

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

	private Terraforming getTerraform(final int subscription) {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);

		// Check the provider support the Terraform generation
		return Optional.ofNullable(locator.getResource(entity.getNode().getId(), Terraforming.class))
				.orElseThrow(() -> new BusinessException("terraform-no-supported", entity.getNode().getRefined().getId()));
	}

	/**
	 * Apply the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @param file
	 *            The target file name.
	 * @return the {@link Response} ready to be consumed.
	 */
	@POST
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform")
	public Response applyTerraform(@PathParam("subscription") final int subscription) {
		final Subscription entity = subscriptionResource.checkVisibleSubscription(subscription);
		final Project project = entity.getProject();
		final Node node = entity.getNode();

		// Check the provider support the Terraform generation
		final Terraforming terra = Optional.ofNullable(locator.getResource(node.getId(), Terraforming.class))
				.orElseThrow(() -> new BusinessException("terraform-no-supported", node.getRefined().getId()));
		log.info("Terraform request for {} ({}, {})", subscription, project.getPkey(), node.getId());
		final StreamingOutput so = o -> {
			final Writer writer = new BufferedWriter(new OutputStreamWriter(o));
			// TODO Need to generate the terraform file into the temporary directory file
			terra.terraform(o, subscription, resource.getConfiguration(subscription));
			applyTerraform(entity, project, node, writer);
		};
		log.info("Terraform finished for {} ({}, {}) : ", subscription, project.getPkey(), node.getId());
		return Response.ok().entity(so).build();

	}

	/**
	 * Prepare the Terraform environment to apply the new environment
	 */
	private void applyTerraform(final Subscription entity, final Project project, final Node node, final Writer writer) {
		try {
			log.info("Terraform start for {} ({}, {})", entity.getId(), entity.getProject().getPkey(), node.getId());
			final String[][] commands = { { "plan", "input=false", "-no-color" }, { "apply", "input=false", "-no-color" },
					{ "show", "input=false", "-no-color" } };
			// Execute Terraform commands
			executeTerraform(entity, project, node, writer, commands);
			writer.flush();
		} catch (final Exception e) {
			log.error("Terraform failed for {}", e, entity.getId());
		}
	}

	/**
	 * Execute the given Terraform commands
	 */
	private void executeTerraform(final Subscription entity, final Project project, final Node node, final Writer writer,
			final String[][] commands) throws InterruptedException, ExecutionException, IOException {
		for (final String[] command : commands) {
			final int code = executeTerraform(writer, command);
			if (code != 1) {
				// Something goes wrong
				log.info("Terraform failed for {} ({}, {}) : {}", entity.getId(), project.getPkey(), node.getId(), code);
				break;
			}
			writer.flush();
		}
	}

	/**
	 * Execute the given Terraform command arguments
	 */
	private int executeTerraform(final Writer writer, final String[] command) throws InterruptedException, ExecutionException {
		final int code = Executors.newSingleThreadExecutor().submit(() -> {
			final ProcessBuilder builder = newBuilder(command);
			final Process process = builder.start();
			// TODO Need to plug IO
			writer.flush();
			return process.waitFor();
		}).get();
		return code;
	}

	/**
	 * A new {@link ProcessBuilder} with the gien arguments
	 * 
	 * @param args
	 * @return
	 */
	protected ProcessBuilder newBuilder(String... args) {
		final ProcessBuilder builder = new ProcessBuilder();
		final String[] osArg = SystemUtils.IS_OS_WINDOWS ? TERRAFORM_COMMAND_WIN : TERRAFORM_COMMAND_LINUX;
		builder.inheritIO();
		builder.directory(new File(System.getProperty("user.home")));
		builder.command(ArrayUtils.addAll(osArg, args));
		return builder;
	}

}
