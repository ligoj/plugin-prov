/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.BooleanUtils;
import org.hibernate.Hibernate;
import org.ligoj.app.plugin.prov.dao.ProvBudgetRepository;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractTermPriceVm;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceScope;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jnellis.binpack.LinearBin;
import net.jnellis.binpack.LinearBinPacker;

/**
 * Budget part of provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL + "/{subscription:\\d+}/budget")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvBudgetResource extends AbstractMultiScopedResource<ProvBudget, ProvBudgetRepository, BudgetEditionVo> {

	private static final String CODE = " (code=";

	@Autowired
	@Getter
	private ProvBudgetRepository repository;

	@Autowired
	private ConfigurationResource configuration;

	/**
	 * Create a budget initiated without any cost.
	 */
	public ProvBudgetResource() {
		super(ResourceScope::getBudget, ResourceScope::setBudget, ProvBudget::new);
	}

	@Override
	protected UpdatedCost saveOrUpdate(final ProvBudget entity, final BudgetEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setName(vo.getName());
		entity.setInitialCost(vo.getInitialCost());

		// Fetch the budgets of this quotes
		final var quote = entity.getConfiguration();
		Hibernate.initialize(quote.getBudgets());

		// Prepare the updated cost of updated instances
		final var relatedCosts = Collections
				.synchronizedMap(new EnumMap<ResourceType, Map<Integer, FloatingCost>>(ResourceType.class));
		// Prevent useless computation, check the relations
		if (entity.getId() != null) {
			// This is an update, update the cost of all related instances
			lean(entity, relatedCosts);
		}

		repository.saveAndFlush(entity);

		// Update accordingly the support costs
		final var cost = new UpdatedCost(entity.getId());
		cost.setRelated(relatedCosts);

		final var updateCost = resource.refreshSupportCost(cost, quote);
		log.info("Total2 monthly cost: {}", updateCost.getTotal().getMin());
		log.info("Total2 initial cost: {}", updateCost.getTotal().getInitial());
		return updateCost;
	}

	/**
	 * Refresh the whole quote budgets.
	 *
	 * @param quote The quote owning the related budget.
	 * @param costs The updated costs and resources.
	 */
	public void lean(final ProvQuote quote, final Map<ResourceType, Map<Integer, FloatingCost>> costs) {
		final var instances = qiRepository.findAll(quote);
		final var databases = qbRepository.findAll(quote);
		final var containers = qcRepository.findAll(quote);
		final var functions = qfRepository.findAll(quote);
		Hibernate.initialize(quote.getUsages());
		Hibernate.initialize(quote.getBudgets());
		lean(quote, instances, databases, containers, functions, costs);

		// Reset the orphan budgets
		final var usedBudgets = Stream.of(instances, databases, containers, functions).flatMap(Collection::stream)
				.map(AbstractQuoteVm::getResolvedBudget).filter(Objects::nonNull).distinct().map(ProvBudget::getId)
				.collect(Collectors.toSet());
		repository.findAll(quote).stream().filter(b -> !usedBudgets.contains(b.getId()))
				.forEach(b -> b.setRequiredInitialCost(0d));
	}

	/**
	 * Detect the related budgets having an initial cost and involved in the given instances/databases collections, lean
	 * them. A refresh is also applied to all resources related to these budgets. This means that some resources not
	 * included in the initial set may be refreshed.
	 *
	 * @param quote      The quote owning the related budget.
	 * @param instances  The instances implied in the current change.
	 * @param databases  The databases implied in the current change.
	 * @param containers The containers implied in the current change.
	 * @param functions  The functions implied in the current change.
	 * @param costs      The updated costs and resources.
	 */
	public void lean(final ProvQuote quote, final List<ProvQuoteInstance> instances,
			final List<ProvQuoteDatabase> databases, final List<ProvQuoteContainer> containers,
			final List<ProvQuoteFunction> functions, final Map<ResourceType, Map<Integer, FloatingCost>> costs) {
		synchronized (quote.getLeanLock()) {
			// Lean all relevant budgets
			final var budgets = Stream.of(instances, databases, containers, functions).flatMap(Collection::stream)
					.map(AbstractQuoteVm::getResolvedBudget).filter(Objects::nonNull)
					.filter(b -> b.getInitialCost() > 0).distinct().toList();
			budgets.forEach(b -> lean(b, costs));

			// Refresh also all remaining resources unrelated to the updated budgets
			refreshNoBudget(instances, ResourceType.INSTANCE, costs, qiResource);
			refreshNoBudget(databases, ResourceType.DATABASE, costs, qbResource);
			refreshNoBudget(containers, ResourceType.CONTAINER, costs, qcResource);
			refreshNoBudget(functions, ResourceType.FUNCTION, costs, qfResource);
		}
	}

	private <T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>, C extends AbstractQuoteVm<P>> void refreshNoBudget(
			final List<C> entities, final ResourceType type, final Map<ResourceType, Map<Integer, FloatingCost>> costs,
			final AbstractProvQuoteVmResource<T, P, C, ?, ?, ?> resource) {
		this.resource.newStream(entities)
				.filter(i -> Optional.ofNullable(i.getResolvedBudget()).map(ProvBudget::getInitialCost).orElse(0d) == 0)
				.forEach(i -> costs.computeIfAbsent(type, k -> new ConcurrentHashMap<>()).put(i.getId(),
						resource.addCost(i, resource::refresh)));
	}

	/**
	 * Detect the related resources to the given budget and refresh them according to the budget constraints.
	 *
	 * @param budget The budget to lean.
	 * @param costs  The updated costs and resources.
	 */
	public void lean(final ProvBudget budget, final Map<ResourceType, Map<Integer, FloatingCost>> costs) {
		if (budget == null) {
			// Ignore, no lean to do
			return;
		}
		Hibernate.initialize(budget.getConfiguration().getUsages());
		Hibernate.initialize(budget.getConfiguration().getBudgets());

		// Get all related resources
		log.info("Lean budget {} in subscription {}", budget.getName(),
				budget.getConfiguration().getSubscription().getId());
		final var instances = getRelated(getRepository()::findRelatedInstances, budget);
		final var databases = getRelated(getRepository()::findRelatedDatabases, budget);
		final var containers = getRelated(getRepository()::findRelatedContainers, budget);
		final var functions = getRelated(getRepository()::findRelatedFunctions, budget);

		// Reset the remaining initial cost
		budget.setRemainingBudget(budget.getInitialCost());
		budget.setRequiredInitialCost(leanRecursive(budget, instances, databases, containers, functions, costs));
		budget.setRemainingBudget(null);
		logLean(c -> {
			log.info("Monthly costs:{}", c.stream().map(i -> i.getPrice().getCost()).toList());
			log.info("Monthly cost: {}", c.stream().mapToDouble(i -> i.getPrice().getCost()).sum());
			log.info("Initial cost: {}", c.stream().mapToDouble(i -> i.getPrice().getInitialCost()).sum());
		}, instances, databases, containers, functions);
	}

	/**
	 * Logger as needed.
	 */
	private void logLean(final Consumer<List<? extends AbstractQuoteVm<?>>> logger,
			final List<ProvQuoteInstance> instances, final List<ProvQuoteDatabase> databases,
			final List<ProvQuoteContainer> containers, final List<ProvQuoteFunction> functions) {
		if (BooleanUtils.toBoolean(configuration.get(ProvResource.SERVICE_KEY + ":log"))) {
			List.of(instances, databases, containers, functions).stream().forEach(logger::accept);
		}
	}

	private <C> void logLean(final Consumer<C> logger, C object) {
		if (BooleanUtils.toBoolean(configuration.get(ProvResource.SERVICE_KEY + ":log"))) {
			logger.accept(object);
		}
	}

	/**
	 * Price priority for packing.
	 */
	private Comparator<? super Entry<Double, AbstractQuoteVm<?>>> priceOrder(
			final Map<AbstractQuoteVm<?>, FloatingPrice<?>> prices) {
		return (e1, e2) -> {
			// Priority to the most expensive price
			final var c1 = prices.get(e1.getValue()).getPrice().getCost();
			final var c2 = prices.get(e2.getValue()).getPrice().getCost();
			var compare = (int) (c2 - c1);
			if (compare == 0) {
				// Then natural naming order
				compare = e1.getValue().getName().compareTo(e2.getValue().getName());
			}
			return compare;
		};
	}

	private double leanRecursive(final ProvBudget budget, final List<ProvQuoteInstance> instances,
			final List<ProvQuoteDatabase> databases, final List<ProvQuoteContainer> containers,
			final List<ProvQuoteFunction> functions, final Map<ResourceType, Map<Integer, FloatingCost>> costs) {
		logLean(c -> log.info("Start lean: {}",
				c.stream().map(i -> i.getName() + CODE + i.getPrice().getCode() + ")").toList()),
				instances, databases, containers, functions);

		// Lookup the best prices
		// And build the pack candidates
		final var packToQr = new IdentityHashMap<Double, AbstractQuoteVm<?>>();
		final var prices = new HashMap<AbstractQuoteVm<?>, FloatingPrice<?>>();
		final var validatedQi = lookup(instances, prices, qiResource, packToQr);
		final var validatedQb = lookup(databases, prices, qbResource, packToQr);
		final var validatedQc = lookup(containers, prices, qcResource, packToQr);
		final var validatedQf = lookup(functions, prices, qfResource, packToQr);

		log.info("Lookup result:                {}",
				prices.entrySet().stream().map(e -> e.getKey().getName() + CODE + e.getKey().getPrice().getCode()
						+ " -> " + e.getValue().getPrice().getCode() + ")").toList());

		// Pack the prices having an initial cost
		var init = pack(budget, packToQr, prices, validatedQi, validatedQb, validatedQc, validatedQf, costs);
		// Commit this pack
		commitPrices(validatedQi, prices, ResourceType.INSTANCE, costs, qiResource);
		commitPrices(validatedQb, prices, ResourceType.DATABASE, costs, qbResource);
		commitPrices(validatedQc, prices, ResourceType.CONTAINER, costs, qcResource);
		commitPrices(validatedQf, prices, ResourceType.FUNCTION, costs, qfResource);
		logLean(t -> {
			log.info("Lean:              {}", t.stream().map(i -> i.getName() + CODE + i.getPrice().getCode() + ")")
					.toList());
			log.info("Lean monthly costs:{}", t.stream().map(i -> i.getPrice().getCost()).toList());
			log.info("Lean monthly cost: {}", t.stream().mapToDouble(i -> i.getPrice().getCost()).sum());
			log.info("Lean initial cost: {}", t.stream().mapToDouble(i -> i.getPrice().getInitialCost()).sum());
		}, validatedQi, validatedQb, validatedQc, validatedQf);
		logLean(c -> log.info("Total intitialCost:{}", c), init);
		return FloatingCost.round(init);
	}

	private double pack(final ProvBudget budget, final Map<Double, AbstractQuoteVm<?>> packToQr,
			final Map<AbstractQuoteVm<?>, FloatingPrice<?>> prices, final List<ProvQuoteInstance> validatedQi,
			final List<ProvQuoteDatabase> validatedQb, final List<ProvQuoteContainer> validatedQc,
			final List<ProvQuoteFunction> validatedQf, final Map<ResourceType, Map<Integer, FloatingCost>> costs) {
		if (packToQr.isEmpty()) {
			return 0d;
		}
		// At least one initial cost is implied, use bin packing strategy
		final var packStart = System.currentTimeMillis();
		final var packer = new LinearBinPacker();
		final var bins = packer.packAll(
				new ArrayList<>(packToQr.entrySet().stream().sorted(priceOrder(prices)).map(Entry::getKey).toList()),
				new ArrayList<>(List.of(new LinearBin(budget.getRemainingBudget()))),
				new ArrayList<>(List.of(Double.MAX_VALUE)));
		final var bin = bins.get(0);
		bin.getPieces().stream().map(packToQr::get).forEach(i -> {
			if (i.getResourceType() == ResourceType.INSTANCE) {
				validatedQi.add((ProvQuoteInstance) i);
			} else if (i.getResourceType() == ResourceType.DATABASE) {
				validatedQb.add((ProvQuoteDatabase) i);
			} else if (i.getResourceType() == ResourceType.CONTAINER) {
				validatedQc.add((ProvQuoteContainer) i);
			} else {
				validatedQf.add((ProvQuoteFunction) i);
			}
		});
		logLean(b -> {
			log.info("Packing result: {}", b.get(0).getPieces().stream().map(packToQr::get)
					.map(i -> i.getName() + CODE + i.getPrice().getCode() + ")").toList());
			log.info("Packing result: {}", b);
		}, bins);
		logPack(packStart, packToQr, budget);

		var init = bin.getTotal();
		if (bins.size() > 1) {
			// Extra bin needs to make a new pass
			budget.setRemainingBudget(FloatingCost.round(budget.getRemainingBudget() - bin.getTotal()));
			final List<ProvQuoteInstance> subQi = newSubPack(packToQr, bins, ResourceType.INSTANCE);
			final List<ProvQuoteDatabase> subQb = newSubPack(packToQr, bins, ResourceType.DATABASE);
			final List<ProvQuoteContainer> subQc = newSubPack(packToQr, bins, ResourceType.CONTAINER);
			final List<ProvQuoteFunction> subQf = newSubPack(packToQr, bins, ResourceType.FUNCTION);
			init += leanRecursive(budget, subQi, subQb, subQc, subQf, costs);
		} else {
			// Pack is completed
		}
		return init;
	}

	/**
	 * Log packing statistics.
	 *
	 * @param packStart Starting timestamp.
	 * @param packToQr  The packed resources.
	 * @param budget    The related budget.
	 */
	protected void logPack(final long packStart, final Map<Double, AbstractQuoteVm<?>> packToQr,
			final ProvBudget budget) {
		// Log packing statistic
		final var packTime = System.currentTimeMillis() - packStart;
		if (packTime > 500) {
			// Enough duration to be logged
			log.info("Packing of {} resources for subscription {} took {}", packToQr.size(),
					budget.getConfiguration().getSubscription().getId(), Duration.ofMillis(packTime));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private <T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>, C extends AbstractQuoteVm<P>> List<C> newSubPack(
			final Map<Double, AbstractQuoteVm<?>> packToQr, final List<LinearBin> bins, final ResourceType type) {
		return (List) bins.get(1).getPieces().stream().map(packToQr::get).filter(i -> i.getResourceType() == type).toList();
	}

	private <T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>, C extends AbstractQuoteVm<P>> void commitPrices(
			final List<C> nodes, final Map<AbstractQuoteVm<?>, FloatingPrice<?>> prices, final ResourceType type,
			final Map<ResourceType, Map<Integer, FloatingCost>> costs,
			final AbstractProvQuoteVmResource<T, P, C, ?, ?, ?> resource) {
		nodes.forEach(i -> {
			@SuppressWarnings("unchecked")
			final var price = (FloatingPrice<P>) prices.get(i);
			costs.computeIfAbsent(type, k -> new HashMap<>()).put(i.getId(), resource.addCost(i, qi -> {
				qi.setPrice(price.getPrice());
				return resource.updateCost(qi);
			}));
		});
	}

	/**
	 * Execute a lookup for each resources, and store the resolved price in the "prices" parameter. Then separate the
	 * resolved prices having an initial cost from the one without. These excluded resources are returned. The prices
	 * having an initial cost are put in the given identity map where the key corresponds to this cost, and the value
	 * corresponds to the resource.
	 */
	private <T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>, C extends AbstractQuoteVm<P>> List<C> lookup(
			final List<C> nodes, final Map<AbstractQuoteVm<?>, FloatingPrice<?>> prices,
			final AbstractProvQuoteVmResource<T, P, C, ?, ?, ?> resource,
			final IdentityHashMap<Double, AbstractQuoteVm<?>> packToQi) {
		final var validatedQi = new ArrayList<C>();
		this.resource.newStream(nodes).forEach(i -> {
			final var price = resource.getNewPrice(i);
			prices.put(i, price);
			if (price.getCost().getInitial() > 0) {
				// Add this price to the pack
				packToQi.put(price.getCost().getInitial(), i);
			} else {
				// Add this price to the commit stage
				validatedQi.add(i);
			}
		});

		return validatedQi;
	}

}
