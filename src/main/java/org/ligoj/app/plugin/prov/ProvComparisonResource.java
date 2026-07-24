/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.dao.ProvComparisonRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvLookupErrorRepository;
import org.ligoj.app.plugin.prov.dao.ProvOptimizerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteContainerRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteFunctionRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.ProvComparison;
import org.ligoj.app.plugin.prov.model.ProvLookupError;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.container.ProvQuoteContainerResource;
import org.ligoj.app.plugin.prov.quote.container.QuoteContainerEditionVo;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.database.QuoteDatabaseEditionVo;
import org.ligoj.app.plugin.prov.quote.function.ProvQuoteFunctionResource;
import org.ligoj.app.plugin.prov.quote.function.QuoteFunctionEditionVo;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceEditionVo;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Cross-provider comparison — a "main subscription" (MS) keeps one or more "compared subscriptions" (CS) as synchronized
 * clones of its quote. Adding a CS resets its quote to a clone of the MS; thereafter each add / update / delete of an MS
 * resource is mirrored onto every CS. When a CS catalog cannot reproduce a resource (no matching price), it is skipped
 * and recorded as a {@link ProvLookupError} so the UI can surface the gap.
 * <p>
 * The clone / mirror reuses the same {@code lookup → validateLookup → saveOrUpdate(quote, entity, vo)} pattern the CSV
 * upload uses per row, so pricing is resolved against the CS catalog. Location is intentionally left to the CS default
 * (regions are provider-specific); user-defined usage / budget / optimizer profiles ARE cloned by name.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvComparisonResource {

	@PersistenceContext
	private EntityManager em;

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvComparisonRepository comparisonRepository;

	@Autowired
	private ProvLookupErrorRepository lookupErrorRepository;

	@Autowired
	private ProvQuoteInstanceResource qiResource;
	@Autowired
	private ProvQuoteDatabaseResource qbResource;
	@Autowired
	private ProvQuoteContainerResource qcResource;
	@Autowired
	private ProvQuoteFunctionResource qfResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;
	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;
	@Autowired
	private ProvQuoteContainerRepository qcRepository;
	@Autowired
	private ProvQuoteFunctionRepository qfRepository;

	@Autowired
	private ProvUsageResource usageResource;
	@Autowired
	private ProvBudgetResource budgetResource;
	@Autowired
	private ProvOptimizerResource optimizerResource;
	@Autowired
	private ProvUsageRepository usageRepository;
	@Autowired
	private ProvBudgetRepository budgetRepository;
	@Autowired
	private ProvOptimizerRepository optimizerRepository;
	@Autowired
	private ProvLocationRepository locationRepository;

	/**
	 * Register a compared subscription (CS) for a main subscription (MS) and reset its quote to a clone of the MS.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 * @param compared     The compared subscription (CS) identifier — its quote is wiped and re-cloned.
	 */
	@POST
	@Path("{subscription:\\d+}/compare/{compared:\\d+}")
	public void addCompared(@PathParam("subscription") final int subscription,
			@PathParam("compared") final int compared) {
		final var ms = subscriptionResource.checkVisible(subscription);
		final var cs = subscriptionResource.checkVisible(compared);
		if (subscription == compared) {
			throw new IllegalArgumentException("compared-self");
		}
		if (comparisonRepository.findByMainSubscriptionIdAndSubscriptionId(subscription, compared) == null) {
			final var link = new ProvComparison();
			link.setMainSubscription(ms);
			link.setSubscription(cs);
			comparisonRepository.saveAndFlush(link);
		}
		clone(ms, cs);
	}

	/**
	 * Return the compared subscriptions (CS) of a main subscription (MS) with their aggregate cost / CO2 and the
	 * resources they could not reproduce.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 * @return The compared subscriptions.
	 */
	@GET
	@Path("{subscription:\\d+}/compare")
	public List<ComparedSubscriptionVo> findAll(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisible(subscription);
		final var out = new ArrayList<ComparedSubscriptionVo>();
		for (final var link : comparisonRepository.findAllByMainSubscriptionId(subscription)) {
			final var csId = link.getSubscription().getId();
			final var vo = new ComparedSubscriptionVo();
			vo.setSubscription(csId);
			final var config = resource.getConfiguration(link.getSubscription());
			vo.setName(config.getName());
			// Floating carries both cost and co2 (min/max), so no separate CO2 field is needed.
			vo.setCost(config.getCost());
			vo.setErrors(lookupErrorRepository.findAllBySubscriptionId(csId));
			out.add(vo);
		}
		return out;
	}

	/**
	 * Unregister a compared subscription (CS): drop the link, wipe its quote resources and clear its recorded errors.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 * @param compared     The compared subscription (CS) identifier.
	 */
	@DELETE
	@Path("{subscription:\\d+}/compare/{compared:\\d+}")
	public void removeCompared(@PathParam("subscription") final int subscription,
			@PathParam("compared") final int compared) {
		subscriptionResource.checkVisible(subscription);
		final var link = comparisonRepository.findByMainSubscriptionIdAndSubscriptionId(subscription, compared);
		if (link != null) {
			comparisonRepository.delete(link);
		}
		lookupErrorRepository.deleteAllBySubscription(compared);
		wipe(compared);
	}

	/**
	 * Re-clone every compared subscription of a main subscription from the current MS quote.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/compare/resync")
	public void resync(@PathParam("subscription") final int subscription) {
		final var ms = subscriptionResource.checkVisible(subscription);
		for (final var link : comparisonRepository.findAllByMainSubscriptionId(subscription)) {
			clone(ms, link.getSubscription());
		}
	}

	/**
	 * Mirror a single MS compute resource onto every CS (incremental per-op sync, called after an MS create / update).
	 * Any existing CS resource with the same name is replaced (upsert by name); unmatched resources are recorded.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 * @param type         The resource type (instance / database / container / function).
	 * @param id           The MS resource identifier that was created or updated.
	 */
	@POST
	@Path("{subscription:\\d+}/compare/mirror/{type}/{id:\\d+}")
	public void mirrorResource(@PathParam("subscription") final int subscription,
			@PathParam("type") final ResourceType type, @PathParam("id") final int id) {
		final var ms = subscriptionResource.checkVisible(subscription);
		final var links = comparisonRepository.findAllByMainSubscriptionId(subscription);
		if (links.isEmpty()) {
			return;
		}
		final var src = findComputeById(type, id);
		if (src == null || src.getConfiguration().getSubscription().getId().intValue() != subscription) {
			return;
		}
		for (final var link : links) {
			final var csQuote = resource.getQuoteFromSubscription(link.getSubscription().getId());
			deleteByName(csQuote, type, src.getName());
			mirrorOne(ms, csQuote, src);
		}
	}

	/**
	 * Mirror an MS resource deletion onto every CS: remove the CS resource(s) with the same name and any recorded error.
	 *
	 * @param subscription The main subscription (MS) identifier.
	 * @param type         The resource type.
	 * @param name         The name of the deleted MS resource.
	 */
	@DELETE
	@Path("{subscription:\\d+}/compare/mirror/{type}/{name}")
	public void mirrorDelete(@PathParam("subscription") final int subscription,
			@PathParam("type") final ResourceType type, @PathParam("name") final String name) {
		subscriptionResource.checkVisible(subscription);
		for (final var link : comparisonRepository.findAllByMainSubscriptionId(subscription)) {
			final var csQuote = resource.getQuoteFromSubscription(link.getSubscription().getId());
			deleteByName(csQuote, type, name);
		}
	}

	/** Load a compute resource by id, or <code>null</code> when the type is not a mirrored compute type. */
	private AbstractQuoteVm<?> findComputeById(final ResourceType type, final int id) {
		return switch (type) {
		case INSTANCE -> qiRepository.findById(id).orElse(null);
		case DATABASE -> qbRepository.findById(id).orElse(null);
		case CONTAINER -> qcRepository.findById(id).orElse(null);
		case FUNCTION -> qfRepository.findById(id).orElse(null);
		default -> null;
		};
	}

	/** Delete every CS compute resource of the given type carrying the given name. */
	private void deleteByName(final ProvQuote csQuote, final ResourceType type, final String name) {
		switch (type) {
		case INSTANCE -> qiRepository.findAll(csQuote).stream().filter(e -> name.equals(e.getName()))
				.forEach(e -> qiResource.delete(e.getId()));
		case DATABASE -> qbRepository.findAll(csQuote).stream().filter(e -> name.equals(e.getName()))
				.forEach(e -> qbResource.delete(e.getId()));
		case CONTAINER -> qcRepository.findAll(csQuote).stream().filter(e -> name.equals(e.getName()))
				.forEach(e -> qcResource.delete(e.getId()));
		case FUNCTION -> qfRepository.findAll(csQuote).stream().filter(e -> name.equals(e.getName()))
				.forEach(e -> qfResource.delete(e.getId()));
		default -> {
			// storage / support not mirrored yet
		}
		}
	}

	/**
	 * Mirror one already-loaded compute resource onto a CS quote. An {@code instanceof} chain (rather than a switch with
	 * an unreachable default) so every branch is exercised by the four compute types.
	 */
	private void mirrorOne(final Subscription ms, final ProvQuote csQuote, final AbstractQuoteVm<?> src) {
		if (src instanceof ProvQuoteInstance i) {
			mirrorInstance(ms, csQuote, i);
		} else if (src instanceof ProvQuoteDatabase d) {
			mirrorDatabase(ms, csQuote, d);
		} else if (src instanceof ProvQuoteContainer c) {
			mirrorContainer(ms, csQuote, c);
		} else {
			mirrorFunction(ms, csQuote, (ProvQuoteFunction) src);
		}
	}

	/**
	 * Clone the whole MS quote into a CS: wipe the CS compute resources, drop its previous errors, then mirror each MS
	 * compute resource. Unmatched resources are recorded as {@link ProvLookupError}.
	 */
	private void clone(final Subscription ms, final Subscription cs) {
		final var csId = cs.getId();
		lookupErrorRepository.deleteAllBySubscription(csId);
		wipe(csId);
		// Flush + clear so the wiped resources leave the context before the re-clone (a
		// stale proxy would otherwise resurface when re-syncing an already-populated CS).
		em.flush();
		em.clear();
		// Clone the quote-level usage / budget / optimizer profiles first — resources
		// reference them by name, and the quote defaults drive pricing. cloneScoped clears
		// the persistence context, so re-fetch both quotes as managed entities afterwards.
		final var csQuote = cloneScoped(resource.getQuoteFromSubscription(ms.getId()), csId,
				resource.getQuoteFromSubscription(csId));
		final var msQuote = resource.getQuoteFromSubscription(ms.getId());
		for (final var e : qiRepository.findAll(msQuote)) {
			mirrorInstance(ms, csQuote, e);
		}
		for (final var e : qbRepository.findAll(msQuote)) {
			mirrorDatabase(ms, csQuote, e);
		}
		for (final var e : qcRepository.findAll(msQuote)) {
			mirrorContainer(ms, csQuote, e);
		}
		for (final var e : qfRepository.findAll(msQuote)) {
			mirrorFunction(ms, csQuote, e);
		}
	}

	/** Wipe all compute resources of a subscription's quote (keeps the quote). */
	private void wipe(final int subscription) {
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		qcResource.deleteAll(subscription);
		qfResource.deleteAll(subscription);
	}

	/**
	 * Clone the MS's usage / budget / optimizer profiles onto the CS by name and re-apply the quote-level defaults, so
	 * resources referencing a profile resolve and pricing matches the MS intent. Must run after {@link #wipe} (so no CS
	 * resource still references a profile) and before the resources are mirrored. Returns the reloaded CS quote.
	 */
	private ProvQuote cloneScoped(final ProvQuote msQuote, final int csId, final ProvQuote csQuote) {
		// Capture the MS default names now — the MS quote is detached by the clear() below.
		final var usageName = msQuote.getUsage() == null ? null : msQuote.getUsage().getName();
		final var budgetName = msQuote.getBudget() == null ? null : msQuote.getBudget().getName();
		final var optimizerName = msQuote.getOptimizer() == null ? null : msQuote.getOptimizer().getName();
		// A quote's location is @NotNull, so it is always present.
		final var locationName = msQuote.getLocation().getName();

		// Drop the CS defaults + existing profiles (idempotent for re-sync). Safe to delete
		// straight through the repositories: the CS has no resources now and the quote no
		// longer references any profile, so there is nothing to reassign.
		csQuote.setUsage(null);
		csQuote.setBudget(null);
		csQuote.setOptimizer(null);
		resource.getRepository().saveAndFlush(csQuote);
		usageRepository.deleteAll(usageRepository.findAll(csQuote));
		budgetRepository.deleteAll(budgetRepository.findAll(csQuote));
		optimizerRepository.deleteAll(optimizerRepository.findAll(csQuote));
		usageRepository.flush();
		budgetRepository.flush();
		optimizerRepository.flush();

		for (final var u : usageRepository.findAll(msQuote)) {
			final var vo = new UsageEditionVo();
			vo.setName(u.getName());
			vo.setRate(u.getRate());
			vo.setDuration(u.getDuration());
			vo.setStart(u.getStart());
			vo.setConvertibleOs(u.getConvertibleOs());
			vo.setConvertibleEngine(u.getConvertibleEngine());
			vo.setConvertibleLocation(u.getConvertibleLocation());
			vo.setConvertibleFamily(u.getConvertibleFamily());
			vo.setConvertibleType(u.getConvertibleType());
			vo.setReservation(u.getReservation());
			usageResource.create(csId, vo);
		}
		for (final var b : budgetRepository.findAll(msQuote)) {
			final var vo = new BudgetEditionVo();
			vo.setName(b.getName());
			vo.setInitialCost(b.getInitialCost());
			budgetResource.create(csId, vo);
		}
		for (final var o : optimizerRepository.findAll(msQuote)) {
			final var vo = new OptimizerEditionVo();
			vo.setName(o.getName());
			vo.setMode(o.getMode());
			vo.setP1TypeOnly(o.getP1TypeOnly());
			optimizerResource.create(csId, vo);
		}

		// Flush + clear so the deleted profiles leave no stale proxy in the context (a merge's
		// dirty check would otherwise try to load a just-deleted default). Then reload a clean
		// CS quote and re-apply the defaults by name.
		em.flush();
		em.clear();
		final var fresh = resource.getQuoteFromSubscription(csId);
		if (usageName != null) {
			fresh.setUsage(usageRepository.findByName(csId, usageName));
		}
		if (budgetName != null) {
			fresh.setBudget(budgetRepository.findByName(csId, budgetName));
		}
		if (optimizerName != null) {
			fresh.setOptimizer(optimizerRepository.findByName(csId, optimizerName));
		}
		// Carry the MS default location when the CS provider offers a same-named region
		// (a light region mapping); otherwise the CS keeps its own default location.
		final var provider = fresh.getSubscription().getNode().getRefined().getId();
		locationRepository.findAllBy("node.id", provider).stream()
				.filter(l -> locationName.equals(l.getName())).findFirst().ifPresent(fresh::setLocation);
		resource.getRepository().saveAndFlush(fresh);
		return fresh;
	}

	private void mirrorInstance(final Subscription ms, final ProvQuote csQuote, final ProvQuoteInstance src) {
		mirror(ms, csQuote, ResourceType.INSTANCE, src, () -> {
			final var vo = new QuoteInstanceEditionVo();
			copyVm(src, vo);
			vo.setOs(src.getOs());
			vo.setSoftware(src.getSoftware());
			vo.setTenancy(src.getTenancy());
			vo.setPrice(qiResource.validateLookup(ResourceType.INSTANCE, qiResource.lookup(csQuote, vo), vo.getName())
					.getId());
			qiResource.saveOrUpdate(csQuote, new ProvQuoteInstance(), vo);
		});
	}

	private void mirrorDatabase(final Subscription ms, final ProvQuote csQuote, final ProvQuoteDatabase src) {
		mirror(ms, csQuote, ResourceType.DATABASE, src, () -> {
			final var vo = new QuoteDatabaseEditionVo();
			copyVm(src, vo);
			vo.setEngine(src.getEngine());
			vo.setEdition(src.getEdition());
			vo.setPrice(qbResource.validateLookup(ResourceType.DATABASE, qbResource.lookup(csQuote, vo), vo.getName())
					.getId());
			qbResource.saveOrUpdate(csQuote, new ProvQuoteDatabase(), vo);
		});
	}

	private void mirrorContainer(final Subscription ms, final ProvQuote csQuote, final ProvQuoteContainer src) {
		mirror(ms, csQuote, ResourceType.CONTAINER, src, () -> {
			final var vo = new QuoteContainerEditionVo();
			copyVm(src, vo);
			vo.setOs(src.getOs());
			vo.setPrice(qcResource.validateLookup(ResourceType.CONTAINER, qcResource.lookup(csQuote, vo), vo.getName())
					.getId());
			qcResource.saveOrUpdate(csQuote, new ProvQuoteContainer(), vo);
		});
	}

	private void mirrorFunction(final Subscription ms, final ProvQuote csQuote, final ProvQuoteFunction src) {
		mirror(ms, csQuote, ResourceType.FUNCTION, src, () -> {
			final var vo = new QuoteFunctionEditionVo();
			copyVm(src, vo);
			vo.setRuntime(src.getRuntime());
			vo.setNbRequests(src.getNbRequests());
			vo.setConcurrency(src.getConcurrency());
			vo.setDuration(src.getDuration());
			vo.setPrice(qfResource.validateLookup(ResourceType.FUNCTION, qfResource.lookup(csQuote, vo), vo.getName())
					.getId());
			qfResource.saveOrUpdate(csQuote, new ProvQuoteFunction(), vo);
		});
	}

	/**
	 * Run a single mirror action, converting any pricing / validation failure into a recorded {@link ProvLookupError}
	 * so it never aborts the surrounding MS operation.
	 */
	private void mirror(final Subscription ms, final ProvQuote csQuote, final ResourceType type,
			final AbstractQuoteVm<?> src, final Runnable action) {
		try {
			action.run();
		} catch (final RuntimeException ex) {
			log.info("Compared subscription {} could not reproduce {} '{}': {}", csQuote.getSubscription().getId(),
					type, src.getName(), ex.getMessage());
			final var error = new ProvLookupError();
			error.setName(src.getName());
			error.setDescription(src.getDescription());
			error.setSubscription(csQuote.getSubscription());
			error.setMainSubscription(ms);
			error.setResourceType(type);
			error.setMainResourceId(src.getId());
			lookupErrorRepository.saveAndFlush(error);
		}
	}

	/**
	 * Copy the provider-neutral requirements of a compute resource onto an edition VO. Location and the specific price
	 * are intentionally NOT copied (location is provider-specific; the price is resolved from a CS lookup). User-defined
	 * usage / budget / optimizer are carried by name (they are cloned into the CS separately).
	 */
	private void copyVm(final AbstractQuoteVm<?> src, final AbstractQuoteVmEditionVo vo) {
		// Copy name + description only — NOT the id: a null id makes this an insert on the
		// CS quote. Copying the id would make saveOrUpdate re-home the MS resource into the
		// CS (moving it out of the MS) instead of cloning it.
		vo.setName(src.getName());
		vo.setDescription(src.getDescription());
		vo.setCpu(src.getCpu());
		vo.setCpuMax(src.getCpuMax());
		vo.setGpu(src.getGpu());
		vo.setGpuMax(src.getGpuMax());
		vo.setRam(src.getRam());
		vo.setRamMax(src.getRamMax());
		vo.setWorkload(src.getWorkload());
		vo.setProcessor(src.getProcessor());
		vo.setArchitecture(src.getArchitecture());
		vo.setPhysical(src.getPhysical());
		vo.setEdge(src.getEdge());
		vo.setMinQuantity(src.getMinQuantity());
		vo.setMaxQuantity(src.getMaxQuantity());
		vo.setLicense(src.getLicense());
		vo.setEphemeral(src.isEphemeral());
		vo.setAutoScale(src.isAutoScale());
		vo.setCpuRate(src.getCpuRate());
		vo.setGpuRate(src.getGpuRate());
		vo.setNetworkRate(src.getNetworkRate());
		vo.setStorageRate(src.getStorageRate());
		vo.setRamRate(src.getRamRate());
		vo.setUsage(Optional.ofNullable(src.getUsage()).map(u -> u.getName()).orElse(null));
		vo.setBudget(Optional.ofNullable(src.getBudget()).map(b -> b.getName()).orElse(null));
		vo.setOptimizer(Optional.ofNullable(src.getOptimizer()).map(o -> o.getName()).orElse(null));
	}
}
