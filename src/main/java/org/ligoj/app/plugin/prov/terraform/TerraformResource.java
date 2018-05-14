package org.ligoj.app.plugin.prov.terraform;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.cache.annotation.CacheRemoveAll;
import javax.cache.annotation.CacheResult;
import javax.transaction.Transactional;
import javax.transaction.Transactional.TxType;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
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

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private SubscriptionRepository subscriptionRepository;

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
	protected TerraformBaseCommand executableCommand;

	@Autowired
	private NodeResource nodeResource;

	/**
	 * Mapping of command name to action. Some of these actions correspond to a real Terraform command, some are just
	 * proxying a Java function.
	 */
	private final Map<String, TerraformAction> commandMapping = new HashMap<>();

	/**
	 * Base constructor initializing the Terraform command mapping.
	 */
	public TerraformResource() {
		// Configure the mapping
		commandMapping.put("generate", new TerraformAction() {

			@Override
			public void execute(final Context context, final OutputStream out, final String... arguments)
					throws IOException {
				getImpl(context.getSubscription().getNode()).generate(context);
			}
		});
		commandMapping.put("secrets", new TerraformAction() {

			@Override
			public void execute(final Context context, final OutputStream out, final String... arguments)
					throws IOException {
				getImpl(context.getSubscription().getNode()).generateSecrets(context);
			}
		});
		commandMapping.put("clean", new TerraformAction() {
			@Override
			public void execute(final Context context, final OutputStream out, final String... arguments)
					throws IOException {
				clean(context.getSubscription());
			}
		});
	}

	/**
	 * Download the Terraform configuration files including the state in zipped format.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @param file
	 *            The target file name.
	 * @return the {@link Response} ready to be consumed.
	 */
	@GET
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	@Path("{subscription:\\d+}/{file:.*.zip}")
	public Response download(@PathParam("subscription") final int subscription, @PathParam("file") final String file) {
		final Subscription subscription2 = subscriptionResource.checkVisible(subscription);
		return AbstractToolPluginResource.download(o -> utils.zip(subscription2, o), file).build();
	}

	/**
	 * Get the log of the current or last Terraform execution of a given subscription.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @return the streaming {@link Response} with output.
	 */
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("{subscription:\\d+}/terraform.log")
	public Response getLog(@PathParam("subscription") final int subscription) {
		final Subscription entity = subscriptionResource.checkVisible(subscription);
		final StreamingOutput so = o -> {
			// Copy log of each command
			for (final String command : Optional.ofNullable(runner.getTask(subscription))
					.map(s -> s.getSequence().split(",")).orElse(ArrayUtils.EMPTY_STRING_ARRAY)) {
				final File log = utils.toFile(entity, command + ".log");
				if (log.exists()) {
					o.write(("---- " + command + " ----\n").getBytes());
					FileUtils.copyFile(log, o);
					o.write("\n".getBytes());
				}
			}
		};
		return Response.ok().entity(so).build();
	}

	/**
	 * Terraform creation sequence.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @param context
	 *            The Terraform user inputs. Will be completed and passed to Terraform commands.
	 * @return The Terraform status. Never <code>null</code>.
	 */
	@POST
	@Path("{subscription:\\d+}/terraform")
	public TerraformStatus create(@PathParam("subscription") final int subscription, final Context context) {
		return sequenceNewThread(subscription, context, TerraformSequence.CREATE);
	}

	/**
	 * Terraform destroy sequence.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @param context
	 *            The Terraform user inputs. Will be completed and passed to Terraform commands.
	 * @return The Terraform status. Never <code>null</code>.
	 */
	@DELETE
	@Path("{subscription:\\d+}/terraform")
	public TerraformStatus destroy(@PathParam("subscription") final int subscription, final Context context) {
		return sequenceNewThread(subscription, context, TerraformSequence.DESTROY);
	}

	/**
	 * Execute a sequence.
	 */
	private TerraformStatus sequenceNewThread(final int subscription, final Context context,
			final TerraformSequence sequence) {
		final Subscription entity = subscriptionResource.checkVisible(subscription);

		// Check the provider support the Terraform generation
		getImpl(entity.getNode());
		log.info("Terraform {} request for {} ({})", sequence, subscription, entity);

		// Save the security context
		final SecurityContext securityContext = SecurityContextHolder.getContext();

		// Create the context
		context.setSubscription(entity);
		context.setSequence(utils.getTerraformCommands(sequence));

		// Trim the user input from the context
		context.getContext().entrySet().forEach(e -> e.setValue(StringUtils.trim(e.getValue())));

		// The Terraform execution will done into another thread
		final TerraformStatus task = startTask(context, sequence);
		Executors.newSingleThreadExecutor().submit(() -> {
			// Restore the context
			log.info("Terraform {} start for {} ({})", sequence, subscription, entity);
			try {
				SecurityContextHolder.setContext(securityContext);
				Thread.sleep(50);
				self.sequenceNewTransaction(context);
				log.info("Terraform {} succeed for {} ({})", sequence, subscription, entity);
			} catch (final Exception e) {
				// The error is not put in the Terraform logger for security
				log.error("Terraform {} failed for {} ({})", sequence, subscription, entity, e);
			}
			return null;
		});
		return task;
	}

	/**
	 * Start a new task with a new context.
	 *
	 * @param context
	 *            The new context.
	 * @param type
	 *            The sequence type.
	 * @return The new Terraform status.
	 */
	protected TerraformStatus startTask(final Context context, final TerraformSequence type) {
		return runner.startTask(context.getSubscription().getNode().getId(), t -> {
			t.setSequence(context.getSequence().stream().map(s -> s[0]).collect(Collectors.joining(",")));
			t.setSubscription(context.getSubscription().getId());
			t.setCommandIndex(null); // Not yet boot
			t.setType(type);
			t.setToAdd(0);
			t.setToDestroy(0);
			t.setToUpdate(0);
			t.setToReplace(0);
			t.setCompleting(0);
			t.setCompleted(0);
		});
	}

	/**
	 * Prepare the Terraform environment to apply/destroy the new environment. Note there is no concurrency check.
	 *
	 * @param context
	 *            The Terraform context holding the subscription, the quote and the user inputs.
	 * @throws IOException
	 *             When files or logs cannot cannot be generated.
	 * @throws InterruptedException
	 *             When Terraform execution has been interrupted.
	 */
	@Transactional(value = TxType.REQUIRES_NEW)
	public void sequenceNewTransaction(final Context context) throws IOException, InterruptedException {
		boolean failed = true;
		try {
			context.setQuote(resource.getConfiguration(context.getSubscription().getId()));
			context.setSubscription(subscriptionRepository.findOneExpected(context.getSubscription().getId()));
			sequenceInternal(context);
			failed = false;
		} finally {
			runner.endTask(context.getSubscription().getNode().getId(), failed);
			// Delete the secret file whatever
			FileUtils.deleteQuietly(utils.toFile(context.getSubscription(), "secrets.auto.tfvars"));
		}
	}

	/**
	 * Delete all files (including logs) that could generated again and we don't need to track.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @throws IOException
	 *             When files or logs cannot cannot be deleted.
	 */
	protected void clean(final Subscription subscription) throws IOException {
		final java.nio.file.Path parent = utils.toFile(subscription).toPath();
		try (Stream<java.nio.file.Path> files = Files.walk(parent)) {
			files.filter(path -> !StringUtils.endsWithAny(path.toString(), ".tfstate", ".tfstate.backup", ".keep.tf",
					".keep.auto.tfvars")).filter(path -> !path.toFile().isDirectory())
					.filter(path -> !path.toString().contains(".terraform")).map(java.nio.file.Path::toFile)
					.forEach(FileUtils::deleteQuietly);
		}
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
	private void sequenceInternal(final Context context) throws InterruptedException, IOException {
		// Execute the sequence
		final Subscription subscription = context.getSubscription();

		// Execute each command
		for (final String[] arguments : context.getSequence()) {
			final String command = arguments[0];

			// Move forward the shared sequence index
			runner.nextStep(subscription.getNode().getId(),
					t -> t.setCommandIndex(t.getCommandIndex() == null ? 0 : t.getCommandIndex() + 1));
			try (FileOutputStream out = new FileOutputStream(utils.toFile(subscription, command + ".log"))) {
				// Execute this command: real Terraform or bean's function
				getAction(command).execute(context, out, arguments);
			}
		}
	}

	/**
	 * Return the executor corresponding to the given command.
	 *
	 * @param command
	 *            The requested command, such as <code>appy</code>, <code>clean</code> ,...
	 * @return The executor corresponding to the given command.
	 */
	protected TerraformAction getAction(final String command) {
		return commandMapping.getOrDefault(command, executableCommand);
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
			final int code = executableCommand.execute(utils.getHome().toFile(), bos, "-v");
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
		result.setLastVersion(utils.getLatestVersion());
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
