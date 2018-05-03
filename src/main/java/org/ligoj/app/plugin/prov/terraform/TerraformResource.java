package org.ligoj.app.plugin.prov.terraform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.dao.SubscriptionRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
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
	 * Terraform version output pattern matcher. Expected output is like : <code>Terraform v0.11.5<code>, and maybe
	 * followed by some outputs like new lines or content to ignore.
	 */
	private static final Pattern TERRFORM_VERSION = Pattern.compile(".* v([^\\s]+)\\s+.*");

	/**
	 * Pattern filtering the row corresponding to a change.
	 */
	private static final Pattern SHOW_CHANGE = Pattern.compile("^\\s*([\\-~+])");

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	protected SubscriptionRepository subscriptionRepository;

	@Autowired
	protected ProvResource resource;

	@Autowired
	protected TerraformRunnerResource runner;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	protected TerraformUtils utils;

	@Autowired
	protected TerraformResource self;

	@Autowired
	protected NodeResource nodeResource;

	/**
	 * Download the Terraform configuration files including the state in zipped format.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @param file
	 *            The target file name.
	 * @return the {@link Response} ready to be consumed.
	 * @throws IOException
	 *             When Terraform content cannot be written.
	 */
	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("{subscription:\\d+}/{file:.*.zip}")
	public Response download(@PathParam("subscription") final int subscription, @PathParam("file") final String file)
			throws IOException {
		final Subscription subscription2 = subscriptionResource.checkVisible(subscription);
		return AbstractToolPluginResource.download(o -> utils.zip(subscription2, o), file).build();
	}

	/**
	 * Get the log of the current or last Terraform execution of a given subscription.
	 * 
	 * @param subscription
	 *            The related subscription.
	 * @return the streaming {@link Response} with output.
	 * @throws IOException
	 *             When Terraform logs cannot be read.
	 */
	@GET
	@Produces(MediaType.TEXT_HTML)
	@Path("{subscription:\\d+}/terraform.log")
	public Response getLog(@PathParam("subscription") final int subscription) throws IOException {
		final Subscription entity = subscriptionResource.checkVisible(subscription);
		final StreamingOutput so = o -> {
			// Copy log of each command
			for (String[] commands : utils.getTerraformCommands()) {
				final File log = utils.toFile(entity, commands[0] + ".log");
				if (log.exists()) {
					FileUtils.copyFile(log, o);
				}
			}
		};
		return Response.ok().entity(so).build();
	}

	/**
	 * Apply (plan, apply, show) the Terraform configuration.
	 * 
	 * @param subscription
	 *            The related subscription.
	 */
	@POST
	@Path("{subscription:\\d+}/terraform")
	public TerraformStatus generateAndExecute(@PathParam("subscription") final int subscription,
			final Context context) {
		final Subscription entity = subscriptionResource.checkVisible(subscription);

		// Check the provider support the Terraform generation
		final Terraforming terra = getImpl(entity.getNode());
		log.info("Terraform request for {} ({})", subscription, entity);
		final SecurityContext securityContext = SecurityContextHolder.getContext();
		// Start the task
		context.setSubscription(entity);
		context.setSequence(utils.getTerraformCommands());
		context.getContext().entrySet().stream().forEach(e -> e.setValue(StringUtils.trim(e.getValue())));

		// The Terraform execution will done into another thread
		final TerraformStatus task = startTask(context);
		Executors.newSingleThreadExecutor().submit(() -> {
			// Restore the context
			log.info("Terraform start for {} ({})", entity.getId(), entity);
			try {
				SecurityContextHolder.setContext(securityContext);
				Thread.sleep(50);
				self.generateAndExecute(terra, context);
				log.info("Terraform succeed for {} ({})", entity.getId(), entity);
			} catch (final Exception e) {
				// The error is not put in the Terraform logger for security
				log.error("Terraform failed for {}", entity, e);
			}
			return null;
		});
		return task;
	}

	protected TerraformStatus startTask(final Context context) {
		final TerraformStatus task = runner.startTask(context.getSubscription().getNode().getId(), t -> {
			t.setCommandIndex(null);
			t.setSequence(context.getSequence().stream().map(s -> s[0]).collect(Collectors.joining(",")));
			t.setToAdd(0);
			t.setToDestroy(0);
			t.setToChange(0);
			t.setCompleting(0);
			t.setCompleted(0);
			t.setSubscription(context.getSubscription().getId());
		});
		return task;
	}

	/**
	 * Prepare the Terraform environment to apply the new environment. Note there is no concurrency check.
	 * 
	 * @param terra
	 *            The Terraforming implementation.
	 * @param context
	 *            The Terraform context holding the subscription, the quote and the user inputs.
	 */
	@Transactional(value = TxType.REQUIRES_NEW)
	public void generateAndExecute(final Terraforming terra, final Context context)
			throws IOException, InterruptedException {

		boolean failed = true;
		try {
			context.setQuote(resource.getConfiguration(context.getSubscription().getId()));
			context.setSubscription(subscriptionRepository.findOneExpected(context.getSubscription().getId()));
			generate(terra, context);
			execute(context);
			failed = false;
		} finally {
			// Delete the secret file whatever
			FileUtils.deleteQuietly(utils.toFile(context.getSubscription(), "secrets.auto.tfvars"));
			runner.endTask(context.getSubscription().getNode().getId(), failed);
		}
	}

	/**
	 * Cleanup and generate the Terraform files
	 */
	private void generate(final Terraforming terra, final Context context) throws IOException {
		// Cleanup the previous generated logs and files
		final java.nio.file.Path parent = utils.toFile(context.getSubscription()).toPath();
		Files.walk(parent).filter(path -> !StringUtils.endsWithAny(path.toString(), ".tfstate", ".tfstate.backup"))
				.filter(path -> !parent.equals(path)).filter(path -> !path.toString().contains(".terraform"))
				.map(java.nio.file.Path::toFile).forEach(FileUtils::deleteQuietly);

		// Write the Terraform configuration files
		terra.generate(context);
	}

	/**
	 * Check the associated provider support Terraform generation and return the corresponding {@link Terraforming}
	 * instance.
	 * 
	 * @param node
	 *            The related node.
	 * @return {@link Terraforming} instance of this node. Never <code>null</code>.
	 */
	private Terraforming getImpl(final Node node) {
		return Optional.ofNullable(locator.getResource(node.getId(), Terraforming.class))
				.orElseThrow(() -> new BusinessException("terraform-no-supported", node.getRefined().getId()));
	}

	/**
	 * Execute the given Terraform commands. Note there is no concurrency check for now.
	 */
	private void execute(final Context context) throws InterruptedException, IOException {
		// Execute the sequence
		final Subscription subscription = context.getSubscription();
		for (final String[] command : context.getSequence()) {
			// Next step, another command
			runner.nextStep(subscription.getNode().getId(),
					t -> t.setCommandIndex(t.getCommandIndex() == null ? 0 : t.getCommandIndex() + 1));
			final FileOutputStream out = new FileOutputStream(utils.toFile(subscription, command[0] + ".log"));
			try {
				handleCode(subscription, out, execute(subscription, out, command));
			} finally {
				IOUtils.closeQuietly(out);
			}
			if ("show".equals(command[0])) {
				computeWorkload(subscription);
			}
		}
	}

	/**
	 * Compute the workload from the plan's log and update the status related to the given subscription.
	 * 
	 * @param subscription
	 *            The subscription related to this operation.
	 * @throws IOException
	 *             When <code>show.log</code> cannot be read.
	 */
	protected void computeWorkload(final Subscription subscription) throws IOException {
		runner.nextStep(subscription.getNode().getId(), t -> computeWorkload(subscription, t));
	}

	/**
	 * Compute the workload from the plan's log.
	 * 
	 * @param subscription
	 *            The subscription related to this operation.
	 * @throws IOException
	 *             When <code>show.log</code> cannot be read.
	 */
	protected void computeWorkload(final Subscription subscription, final TerraformStatus status) {
		final AtomicInteger added = new AtomicInteger();
		final AtomicInteger deleted = new AtomicInteger();
		final AtomicInteger updated = new AtomicInteger();

		// Iterate over each line
		try (Stream<String> stream = Files.lines(utils.toFile(subscription, "show.log").toPath())) {
			stream.map(SHOW_CHANGE::matcher).filter(Matcher::find).forEach(matcher -> {
				// Detect the type of this change
				final char type = matcher.group(1).charAt(0);
				if (type == '+') {
					added.incrementAndGet();
				} else if (type == '-') {
					deleted.incrementAndGet();
				} else {
					updated.incrementAndGet();
				}
			});
		} catch (final IOException e) {
			log.warn("Unable to get the full workload from the 'show' command", e);
		}
		// Update the status
		status.setToAdd(added.get());
		status.setToDestroy(deleted.get());
		status.setToChange(updated.get());
	}

	private void handleCode(final Subscription subscription, final FileOutputStream out, final int code)
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

	/**
	 * Execute the given Terraform command arguments
	 */
	private int execute(final Subscription subscription, final OutputStream out, final String... command)
			throws InterruptedException, IOException {
		return execute(utils.toFile(subscription), out, command);
	}

	/**
	 * Execute the given Terraform command arguments
	 */
	private int execute(final File directory, final OutputStream out, final String... command)
			throws InterruptedException, IOException {
		FileUtils.forceMkdir(directory);
		final ProcessBuilder builder = utils.newBuilder(command).redirectErrorStream(true).directory(directory);
		final Process process = builder.start();
		process.getInputStream().transferTo(out);

		// Wait and get the code
		final int code = process.waitFor();
		out.flush();
		return code;
	}

	/**
	 * Return the Terraform versions : current and latest version.
	 * 
	 * @return The terraform version when succeed, or <code>null</code>. Is cached.
	 * @throws IOException
	 *             Stream cannot be read.
	 * @throws InterruptedException
	 *             The process cannot be executed.
	 */
	@CacheResult(cacheName = "terraform-version")
	@Path("terraform/install")
	@GET
	public TerraformInformation getVersion() throws IOException, InterruptedException {
		nodeResource.checkWritableNode(ProvResource.SERVICE_KEY);
		final TerraformInformation result = new TerraformInformation();
		if (utils.isInstalled()) {
			result.setInstalled(true);
			final ByteArrayOutputStream bos = new ByteArrayOutputStream();
			final int code = execute(utils.getHome().toFile(), bos, "-v");
			final String output = bos.toString();
			if (code == 0) {
				// Terraform v0.11.5
				final Matcher matcher = TERRFORM_VERSION.matcher(output);
				if (matcher.find()) {
					result.setVersion(matcher.group(1));
				}
			} else {
				log.error("Unable to get Terraform version, code: {}, output: {}", code, output);
			}
		}
		result.setLastVersion(utils.getLastestVersion());
		return result;
	}

	/**
	 * Install or update the Terraform binary. Note for now there is no check about current <code>terraform</code>
	 * command is running and may this update to fail.
	 * 
	 * @param version
	 *            The target version.
	 * @return The installed and checked Terraform version.
	 * @throws IOException
	 *             Stream cannot be read.
	 * @throws InterruptedException
	 *             The process cannot be executed.
	 */
	@CacheRemoveAll(cacheName = "terraform-version")
	@Path("terraform/version/{version:\\d+\\.\\d+\\.\\d+.*}")
	@POST
	public TerraformInformation install(@PathParam("version") final String version)
			throws IOException, InterruptedException {
		nodeResource.checkWritableNode(ProvResource.SERVICE_KEY);
		utils.install(version);
		return getVersion();
	}
}
