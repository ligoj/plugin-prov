/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.ligoj.app.plugin.prov.dao.*;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.app.plugin.prov.quote.container.ProvQuoteContainerResource;
import org.ligoj.app.plugin.prov.quote.container.QuoteContainerEditionVo;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.database.QuoteDatabaseEditionVo;
import org.ligoj.app.plugin.prov.quote.function.ProvQuoteFunctionResource;
import org.ligoj.app.plugin.prov.quote.function.QuoteFunctionEditionVo;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceEditionVo;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.plugin.prov.quote.storage.QuoteStorageEditionVo;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;

/**
 * Quote snapshots — immutable, named copies of a quote configuration that can be listed, diffed (client-side, from the
 * returned document) and restored. The document is a compact, versioned JSON layout owned by this resource: quote
 * defaults + scoped profiles + one normalized row per resource. Rows use the edition-VO property names so a restore is
 * a plain Jackson bind followed by the same {@code lookup → validateLookup → saveOrUpdate} replay the CSV upload and
 * the compared-subscription clone use — prices are re-resolved against the current catalog, and rows that no longer
 * match any offer are reported (not silently dropped).
 * <p>
 * Restore covers the compute types, their storages, the scoped profiles and the quote defaults. Support resources are
 * captured in the document (so a diff can show them) but are neither wiped nor restored.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvQuoteSnapshotResource {

	/**
	 * Retention cap: oldest snapshots beyond this count are culled at creation.
	 */
	protected static final int MAX_SNAPSHOTS = 50;

	/**
	 * Lenient mapper: display-only row fields (cost, term, typeName…) are simply ignored when binding an edition VO.
	 */
	private static final JsonMapper MAPPER = new JsonMapper(JsonMapper.builderWithJackson2Defaults()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));

	@PersistenceContext
	private EntityManager em;

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteSnapshotRepository repository;

	@Autowired
	private ProvQuoteInstanceResource qiResource;
	@Autowired
	private ProvQuoteDatabaseResource qbResource;
	@Autowired
	private ProvQuoteContainerResource qcResource;
	@Autowired
	private ProvQuoteFunctionResource qfResource;
	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvInstancePriceRepository ipRepository;
	@Autowired
	private ProvDatabasePriceRepository bpRepository;
	@Autowired
	private ProvContainerPriceRepository cpRepository;
	@Autowired
	private ProvFunctionPriceRepository fpRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;
	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;
	@Autowired
	private ProvQuoteContainerRepository qcRepository;
	@Autowired
	private ProvQuoteFunctionRepository qfRepository;
	@Autowired
	private ProvQuoteStorageRepository qsRepository;
	@Autowired
	private ProvQuoteSupportRepository qs2Repository;

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
	 * Create a snapshot of the current quote configuration.
	 *
	 * @param subscription The subscription identifier.
	 * @param vo           The snapshot label ({@code name}) and optional description.
	 * @return The created snapshot identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/snapshot")
	public int create(@PathParam("subscription") final int subscription, final DescribedBean<Integer> vo) {
		subscriptionResource.checkVisible(subscription);
		final var quote = resource.getQuoteFromSubscription(subscription);
		final var entity = new ProvQuoteSnapshot();
		entity.setName(vo.getName());
		entity.setDescription(vo.getDescription());
		entity.setSubscription(quote.getSubscription());
		entity.setCost(quote.getCost());
		entity.setMaxCost(quote.getMaxCost());
		entity.setCo2(quote.getCo2());
		entity.setMaxCo2(quote.getMaxCo2());
		final var document = buildDocument(quote);
		entity.setNbResources(((Collection<?>) document.get("resources")).size());
		try {
			entity.setData(MAPPER.writeValueAsString(document));
		} catch (final Exception e) {
			throw new IllegalStateException("snapshot-serialize", e);
		}
		repository.saveAndFlush(entity);

		// Retention: cull the oldest snapshots beyond the cap.
		final var all = repository.findAllBySubscriptionIdOrderByIdDesc(subscription);
		all.stream().skip(MAX_SNAPSHOTS).forEach(repository::delete);
		return entity.getId();
	}

	/**
	 * Return the snapshots of a subscription (metadata only, newest first).
	 *
	 * @param subscription The subscription identifier.
	 * @return The snapshots, newest first.
	 */
	@GET
	@Path("{subscription:\\d+}/snapshot")
	public List<ProvQuoteSnapshot> findAll(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisible(subscription);
		return repository.findAllBySubscriptionIdOrderByIdDesc(subscription);
	}

	/**
	 * Return a snapshot's configuration document.
	 *
	 * @param subscription The subscription identifier.
	 * @param id           The snapshot identifier.
	 * @return The stored document (quote defaults, profiles, normalized resource rows).
	 */
	@GET
	@Path("{subscription:\\d+}/snapshot/{id:\\d+}")
	public JsonNode getDocument(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		try {
			return MAPPER.readTree(findOwned(subscription, id).getData());
		} catch (final Exception e) {
			throw new IllegalStateException("snapshot-parse", e);
		}
	}

	/**
	 * Delete a snapshot.
	 *
	 * @param subscription The subscription identifier.
	 * @param id           The snapshot identifier.
	 */
	@DELETE
	@Path("{subscription:\\d+}/snapshot/{id:\\d+}")
	public void delete(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		repository.delete(findOwned(subscription, id));
	}

	/**
	 * Restore the quote from a snapshot: wipe the compute resources + storages + profiles, then replay the document.
	 * Prices are re-resolved against the current catalog; rows without a matching offer any more are skipped and
	 * reported. Support resources are left untouched.
	 *
	 * @param subscription The subscription identifier.
	 * @param id           The snapshot identifier.
	 * @return The names of the rows that could not be restored (no matching price), empty when fully restored.
	 */
	@POST
	@Path("{subscription:\\d+}/snapshot/{id:\\d+}/restore")
	public List<String> restore(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		final var document = getDocument(subscription, id);

		// Wipe: storages first (also detached from their VMs), then every compute type.
		qsResource.deleteAll(subscription);
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		qcResource.deleteAll(subscription);
		qfResource.deleteAll(subscription);
		em.flush();
		em.clear();

		// Profiles: drop then recreate from the document, then re-apply the quote defaults.
		final var stale = resource.getQuoteFromSubscription(subscription);
		stale.setUsage(null);
		stale.setBudget(null);
		stale.setOptimizer(null);
		resource.getRepository().saveAndFlush(stale);
		usageRepository.deleteAll(usageRepository.findAll(stale));
		budgetRepository.deleteAll(budgetRepository.findAll(stale));
		optimizerRepository.deleteAll(optimizerRepository.findAll(stale));
		usageRepository.flush();
		budgetRepository.flush();
		optimizerRepository.flush();
		em.flush();
		em.clear();
		for (final var node : document.withArray("usages")) {
			usageResource.create(subscription, MAPPER.convertValue(node, UsageEditionVo.class));
		}
		for (final var node : document.withArray("budgets")) {
			budgetResource.create(subscription, MAPPER.convertValue(node, BudgetEditionVo.class));
		}
		for (final var node : document.withArray("optimizers")) {
			optimizerResource.create(subscription, MAPPER.convertValue(node, OptimizerEditionVo.class));
		}
		// Flush + clear so the quote's profile collections are re-read fresh — a stale
		// collection cached before the creations would make the replay's profile-by-name
		// resolution (and the budget lean recompute) miss the recreated profiles.
		em.flush();
		em.clear();

		final var quote = resource.getQuoteFromSubscription(subscription);
		applyText(document, "usage", n -> quote.setUsage(usageRepository.findByName(subscription, n)));
		applyText(document, "budget", n -> quote.setBudget(budgetRepository.findByName(subscription, n)));
		applyText(document, "optimizer", n -> quote.setOptimizer(optimizerRepository.findByName(subscription, n)));
		applyText(document, "location", n -> locationRepository
				.findAllBy("node.id", quote.getSubscription().getNode().getRefined().getId()).stream()
				.filter(l -> n.equals(l.getName())).findFirst().ifPresent(quote::setLocation));
		resource.getRepository().saveAndFlush(quote);

		// Replay: compute rows first (tracking the new ids), then their storages.
		final var failed = new ArrayList<String>();
		final var idByName = new LinkedHashMap<String, Integer>();
		final var storageRows = new ArrayList<JsonNode>();
		for (final var row : document.withArray("resources")) {
			final var type = row.path("resourceType").asString();
			if ("storage".equals(type)) {
				storageRows.add(row);
			} else if (!"support".equals(type)) {
				restoreCompute(quote, type, row, idByName, failed);
			}
		}
		for (final var row : storageRows) {
			restoreStorage(subscription, row, idByName, failed);
		}
		return failed;
	}

	private void applyText(final JsonNode document, final String field, final java.util.function.Consumer<String> apply) {
		final var value = document.path(field).asString(null);
		if (value != null && !value.isEmpty()) {
			apply.accept(value);
		}
	}

	/**
	 * Replay one compute row: bind the edition VO, restore the exact snapshotted price when its code still exists in
	 * the catalog (maximal fidelity — e.g. a committed term the default lookup constraints would not pick), else fall
	 * back to a fresh lookup. Failures are reported, not fatal.
	 */
	private void restoreCompute(final ProvQuote quote, final String type, final JsonNode row,
			final Map<String, Integer> idByName, final List<String> failed) {
		final var name = row.path("name").asString();
		final var code = row.path("priceCode").asString(null);
		final var provider = quote.getSubscription().getNode().getRefined().getId();
		try {
			final int newId = switch (type) {
				case "instance" -> {
					final var vo = MAPPER.convertValue(row, QuoteInstanceEditionVo.class);
					vo.setSubscription(quote.getSubscription().getId());
					final var price = byCode(ipRepository.findAllBy("code", code), provider);
					vo.setPrice(price != null ? price.getId()
							: qiResource.validateLookup(ResourceType.INSTANCE, qiResource.lookup(quote, vo), name).getId());
					yield qiResource.saveOrUpdate(quote, new ProvQuoteInstance(), vo).getId();
				}
				case "database" -> {
					final var vo = MAPPER.convertValue(row, QuoteDatabaseEditionVo.class);
					vo.setSubscription(quote.getSubscription().getId());
					final var price = byCode(bpRepository.findAllBy("code", code), provider);
					vo.setPrice(price != null ? price.getId()
							: qbResource.validateLookup(ResourceType.DATABASE, qbResource.lookup(quote, vo), name).getId());
					yield qbResource.saveOrUpdate(quote, new ProvQuoteDatabase(), vo).getId();
				}
				case "container" -> {
					final var vo = MAPPER.convertValue(row, QuoteContainerEditionVo.class);
					vo.setSubscription(quote.getSubscription().getId());
					final var price = byCode(cpRepository.findAllBy("code", code), provider);
					vo.setPrice(price != null ? price.getId()
							: qcResource.validateLookup(ResourceType.CONTAINER, qcResource.lookup(quote, vo), name).getId());
					yield qcResource.saveOrUpdate(quote, new ProvQuoteContainer(), vo).getId();
				}
				default -> {
					final var vo = MAPPER.convertValue(row, QuoteFunctionEditionVo.class);
					vo.setSubscription(quote.getSubscription().getId());
					final var price = byCode(fpRepository.findAllBy("code", code), provider);
					vo.setPrice(price != null ? price.getId()
							: qfResource.validateLookup(ResourceType.FUNCTION, qfResource.lookup(quote, vo), name).getId());
					yield qfResource.saveOrUpdate(quote, new ProvQuoteFunction(), vo).getId();
				}
			};
			idByName.put(type + ":" + name, newId);
		} catch (final RuntimeException e) {
			log.info("Snapshot restore could not replay {} '{}': {}", type, name, e.getMessage());
			failed.add(name);
		}
	}

	/**
	 * The catalog price carrying the snapshotted code on the right provider node, or <code>null</code>.
	 */
	private <P extends AbstractPrice<?>> P byCode(final List<P> candidates, final String provider) {
		return candidates.stream().filter(p -> p.getType().getNode().getId().equals(provider)).findFirst().orElse(null);
	}

	/**
	 * Replay one storage row, re-linked to its (freshly recreated) parent VM by name.
	 */
	private void restoreStorage(final int subscription, final JsonNode row, final Map<String, Integer> idByName,
			final List<String> failed) {
		final var name = row.path("name").asString();
		try {
			final var vo = MAPPER.convertValue(row, QuoteStorageEditionVo.class);
			vo.setSubscription(subscription);
			final var parent = row.path("parent").asString(null);
			final var parentType = row.path("parentType").asString(null);
			if (parent != null && parentType != null) {
				final var parentId = idByName.get(parentType + ":" + parent);
				if (parentId == null) {
					// The parent itself could not be restored: skip this dependent storage.
					throw new EntityNotFoundException(parent);
				}
				switch (parentType) {
					case "instance" -> vo.setInstance(parentId);
					case "database" -> vo.setDatabase(parentId);
					case "container" -> vo.setContainer(parentId);
					default -> vo.setFunction(parentId);
				}
			}
			qsResource.create(vo);
		} catch (final RuntimeException e) {
			log.info("Snapshot restore could not replay storage '{}': {}", name, e.getMessage());
			failed.add(name);
		}
	}

	/**
	 * Load a snapshot and check it belongs to the (visible) subscription.
	 */
	private ProvQuoteSnapshot findOwned(final int subscription, final int id) {
		subscriptionResource.checkVisible(subscription);
		final var entity = repository.findOneExpected(id);
		if (entity.getSubscription().getId() != subscription) {
			throw new EntityNotFoundException(String.valueOf(id));
		}
		return entity;
	}

	/**
	 * Build the versioned snapshot document from the live quote. Rows carry the edition-VO property names (for the
	 * restore bind) plus display-only figures (cost / co2 / resolved type & term) for the client-side diff.
	 */
	private Map<String, Object> buildDocument(final ProvQuote quote) {
		final var document = new LinkedHashMap<String, Object>();
		document.put("version", 1);
		document.put("location", Optional.ofNullable(quote.getLocation()).map(AbstractNamedEntity::getName).orElse(null));
		document.put("usage", Optional.ofNullable(quote.getUsage()).map(AbstractNamedEntity::getName).orElse(null));
		document.put("budget", Optional.ofNullable(quote.getBudget()).map(AbstractNamedEntity::getName).orElse(null));
		document.put("optimizer", Optional.ofNullable(quote.getOptimizer()).map(AbstractNamedEntity::getName).orElse(null));

		document.put("usages", usageRepository.findAll(quote).stream().map(u -> {
			final var m = new LinkedHashMap<String, Object>();
			m.put("name", u.getName());
			m.put("rate", u.getRate());
			m.put("duration", u.getDuration());
			m.put("start", u.getStart());
			m.put("convertibleOs", u.getConvertibleOs());
			m.put("convertibleEngine", u.getConvertibleEngine());
			m.put("convertibleLocation", u.getConvertibleLocation());
			m.put("convertibleFamily", u.getConvertibleFamily());
			m.put("convertibleType", u.getConvertibleType());
			m.put("reservation", u.getReservation());
			return m;
		}).toList());
		document.put("budgets", budgetRepository.findAll(quote).stream().map(b -> {
			final var m = new LinkedHashMap<String, Object>();
			m.put("name", b.getName());
			m.put("initialCost", b.getInitialCost());
			return m;
		}).toList());
		document.put("optimizers", optimizerRepository.findAll(quote).stream().map(o -> {
			final var m = new LinkedHashMap<String, Object>();
			m.put("name", o.getName());
			m.put("mode", o.getMode());
			m.put("p1TypeOnly", o.getP1TypeOnly());
			return m;
		}).toList());

		final var resources = new ArrayList<Map<String, Object>>();
		qiRepository.findAll(quote).forEach(e -> {
			final var m = vmRow("instance", e);
			m.put("os", e.getOs());
			m.put("software", e.getSoftware());
			m.put("tenancy", e.getTenancy());
			m.put("maxVariableCost", e.getMaxVariableCost());
			resources.add(m);
		});
		qbRepository.findAll(quote).forEach(e -> {
			final var m = vmRow("database", e);
			m.put("engine", e.getEngine());
			// Effective edition: the save invariant forces entity.edition == price.edition, so
			// fall back to the price's (raw fixture data may miss it) — the replay's
			// checkAttribute would otherwise reject the restored price.
			m.put("edition", Optional.ofNullable(e.getEdition())
					.orElseGet(() -> Optional.ofNullable(e.getPrice()).map(ProvDatabasePrice::getEdition).orElse(null)));
			resources.add(m);
		});
		qcRepository.findAll(quote).forEach(e -> {
			final var m = vmRow("container", e);
			m.put("os", e.getOs());
			resources.add(m);
		});
		qfRepository.findAll(quote).forEach(e -> {
			final var m = vmRow("function", e);
			m.put("runtime", e.getRuntime());
			m.put("nbRequests", e.getNbRequests());
			m.put("concurrency", e.getConcurrency());
			m.put("duration", e.getDuration());
			resources.add(m);
		});
		qsRepository.findAll(quote).forEach(e -> resources.add(storageRow(e)));
		qs2Repository.findAll(quote).forEach(e -> {
			// Display-only: supports are neither wiped nor restored.
			final var m = baseRow("support", e.getName(), e.getDescription(), e.getCost(), e.getMaxCost(), 0d, 0d);
			m.put("seats", e.getSeats());
			m.put("typeName", Optional.ofNullable(e.getPrice()).map(p -> p.getType().getName()).orElse(null));
			resources.add(m);
		});
		document.put("resources", resources);
		return document;
	}

	private LinkedHashMap<String, Object> baseRow(final String resourceType, final String name,
			final String description, final double cost, final double maxCost, final double co2, final double maxCo2) {
		final var m = new LinkedHashMap<String, Object>();
		m.put("resourceType", resourceType);
		m.put("name", name);
		m.put("description", description);
		m.put("cost", cost);
		m.put("maxCost", maxCost);
		m.put("co2", co2);
		m.put("maxCo2", maxCo2);
		return m;
	}

	/**
	 * Common row of a VM-like resource: restore fields (edition-VO names) + display-only resolved price facts.
	 */
	private LinkedHashMap<String, Object> vmRow(final String resourceType, final AbstractQuoteVm<?> e) {
		final var m = baseRow(resourceType, e.getName(), e.getDescription(), e.getCost(), e.getMaxCost(), e.getCo2(),
				e.getMaxCo2());
		m.put("cpu", e.getCpu());
		m.put("cpuMax", e.getCpuMax());
		m.put("gpu", e.getGpu());
		m.put("gpuMax", e.getGpuMax());
		m.put("ram", e.getRam());
		m.put("ramMax", e.getRamMax());
		m.put("workload", e.getWorkload());
		m.put("processor", e.getProcessor());
		m.put("architecture", e.getArchitecture());
		m.put("physical", e.getPhysical());
		m.put("edge", e.getEdge());
		m.put("internet", e.getInternet());
		m.put("minQuantity", e.getMinQuantity());
		m.put("maxQuantity", e.getMaxQuantity());
		m.put("license", e.getLicense());
		m.put("ephemeral", e.isEphemeral());
		m.put("autoScale", e.isAutoScale());
		m.put("cpuRate", e.getCpuRate());
		m.put("gpuRate", e.getGpuRate());
		m.put("ramRate", e.getRamRate());
		m.put("networkRate", e.getNetworkRate());
		m.put("storageRate", e.getStorageRate());
		m.put("usage", Optional.ofNullable(e.getUsage()).map(AbstractNamedEntity::getName).orElse(null));
		m.put("budget", Optional.ofNullable(e.getBudget()).map(AbstractNamedEntity::getName).orElse(null));
		m.put("optimizer", Optional.ofNullable(e.getOptimizer()).map(AbstractNamedEntity::getName).orElse(null));
		m.put("location", Optional.ofNullable(e.getLocation()).map(AbstractNamedEntity::getName).orElse(null));
		// Exact price for the fidelity restore + display-only resolved facts for the diff
		// (all ignored by the restore VO bind).
		m.put("priceCode", Optional.ofNullable(e.getPrice()).map(AbstractPrice::getCode).orElse(null));
		m.put("typeName", Optional.ofNullable(e.getPrice()).map(p -> p.getType().getName()).orElse(null));
		m.put("term", Optional.ofNullable(e.getPrice()).map(p -> p.getTerm().getName()).orElse(null));
		return m;
	}

	private LinkedHashMap<String, Object> storageRow(final ProvQuoteStorage e) {
		final var m = baseRow("storage", e.getName(), e.getDescription(), e.getCost(), e.getMaxCost(), 0d, 0d);
		m.put("size", e.getSize());
		m.put("sizeMax", e.getSizeMax());
		m.put("latency", e.getLatency());
		m.put("optimized", e.getOptimized());
		m.put("quantity", e.getQuantity());
		m.put("location", Optional.ofNullable(e.getLocation()).map(AbstractNamedEntity::getName).orElse(null));
		// The storage price is strictly resolved by type code — keep it for the restore.
		final var typeCode = Optional.ofNullable(e.getPrice()).map(p -> p.getType().getCode()).orElse(null);
		m.put("type", typeCode);
		m.put("typeName", Optional.ofNullable(e.getPrice()).map(p -> p.getType().getName()).orElse(typeCode));
		// Parent VM by name — ids change on restore.
		if (e.getQuoteInstance() != null) {
			m.put("parentType", "instance");
			m.put("parent", e.getQuoteInstance().getName());
		} else if (e.getQuoteDatabase() != null) {
			m.put("parentType", "database");
			m.put("parent", e.getQuoteDatabase().getName());
		} else if (e.getQuoteContainer() != null) {
			m.put("parentType", "container");
			m.put("parent", e.getQuoteContainer().getName());
		} else if (e.getQuoteFunction() != null) {
			m.put("parentType", "function");
			m.put("parent", e.getQuoteFunction().getName());
		}
		return m;
	}
}
