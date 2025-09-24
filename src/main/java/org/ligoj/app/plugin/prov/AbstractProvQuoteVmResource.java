/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.persistence.EntityNotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.ligoj.app.plugin.prov.dao.*;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The resource part of the provisioning of a VM like type.
 *
 * @param <T> The instance resource type.
 * @param <P> Quoted resource price type.
 * @param <C> Quoted resource type.
 * @param <E> Quoted resource edition VO type.
 * @param <L> Quoted resource lookup result type.
 * @param <Q> Quoted resource details type.
 */
@Slf4j
public abstract class AbstractProvQuoteVmResource<T extends AbstractInstanceType, P extends AbstractTermPriceVm<T>, C extends AbstractQuoteVm<P>, E extends AbstractQuoteVmEditionVo, L extends AbstractLookup<P>, Q extends QuoteVm>
		extends AbstractProvQuoteResource<T, P, C, E> {

	/**
	 * The default usage : 100% for 1 month.
	 */
	public static final ProvUsage USAGE_DEFAULT = new ProvUsage();

	/**
	 * The default budget : no initial cost.
	 */
	protected static final ProvBudget BUDGET_DEFAULT = new ProvBudget();

	/**
	 * The default optimizer : cost optimization.
	 */
	protected static final ProvOptimizer OPTIMIZER_DEFAULT = new ProvOptimizer();

	@Autowired
	protected ProvQuoteStorageResource storageResource;

	@Autowired
	protected ProvInstancePriceTermRepository iptRepository;

	@Autowired
	protected ProvLocationRepository locationRepository;

	@Autowired
	protected ProvQuoteStorageRepository qsRepository;

	@Autowired
	protected ProvBudgetResource budgetResource;

	@Autowired
	protected ServicePluginLocator locator;

	@Override
	public abstract BaseProvTermPriceRepository<T, P> getIpRepository();

	@Override
	public abstract BaseProvInstanceTypeRepository<T> getItRepository();

	@Override
	public UpdatedCost update(final E vo) {
		return saveOrUpdate(resource.findConfigured(getQiRepository(), vo.getId()), vo);
	}

	/**
	 * Save or update the given entity from the {@link AbstractQuoteVm}. The computed cost are recursively updated from
	 * the resource to the quote total cost.
	 *
	 * @param entity The entity to update.
	 * @param vo     The change to apply to the entity.
	 * @return The updated cost including the related ones.
	 */
	public UpdatedCost saveOrUpdate(final C entity, final E vo) {
		// Check the associations and proceed
		return saveOrUpdate(getQuoteFromSubscription(vo.getSubscription()), entity, vo);
	}

	/**
	 * Save or update the given entity from the {@link AbstractQuoteVm}. The computed cost are recursively updated from
	 * the resource to the quote total cost. The change must contain a price retrieved from a previous lookup. The
	 * match is not performed by the following function.
	 *
	 * @param quote  The related quote.
	 * @param entity The entity to update.
	 * @param vo     The new value to apply to the entity.
	 * @return The updated cost including the related ones.
	 */
	public UpdatedCost saveOrUpdate(final ProvQuote quote, final C entity, final E vo) {
		// Compute the unbound cost delta
		final var deltaUnbound = BooleanUtils.toInteger(vo.getMaxQuantity() == null)
				- BooleanUtils.toInteger(entity.isUnboundCost());

		// Check the associations and copy attributes to the entity
		final var subscription = quote.getSubscription();
		final var providerId = subscription.getNode().getRefined().getId();
		DescribedBean.copy(vo, entity);
		entity.setConfiguration(quote);
		final var oldLocation = entity.getResolvedLocation();
		final var oldBudget = entity.getResolvedBudget();
		entity.setPrice(getIpRepository().findOneExpected(vo.getPrice()));
		resource.checkVisibility(entity.getPrice().getType(), providerId);
		entity.setLocation(resource.findLocation(providerId, vo.getLocation()));
		entity.setUsage(Optional.ofNullable(vo.getUsage()).map(u -> getUsage(quote, u)).orElse(null));
		entity.setBudget(Optional.ofNullable(vo.getBudget()).map(u -> getBudget(quote, u)).orElse(null));
		entity.setOptimizer(Optional.ofNullable(vo.getOptimizer()).map(u -> getOptimizer(quote, u)).orElse(null));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		entity.setGpu(vo.getGpu());
		entity.setProcessor(vo.getProcessor());
		entity.setWorkload(vo.getWorkload());
		entity.setPhysical(vo.getPhysical());
		entity.setMinQuantity(vo.getMinQuantity());
		entity.setMaxQuantity(vo.getMaxQuantity());
		entity.setLicense(Optional.ofNullable(vo.getLicense()).map(StringUtils::upperCase).orElse(null));
		entity.setRamMax(vo.getRamMax());
		entity.setCpuMax(vo.getCpuMax());
		entity.setGpuMax(vo.getGpuMax());
		entity.setAutoScale(vo.isAutoScale());
		entity.setRamRate(vo.getRamRate());
		entity.setCpuRate(vo.getCpuRate());
		entity.setGpuRate(vo.getGpuRate());
		entity.setNetworkRate(vo.getNetworkRate());
		entity.setStorageRate(vo.getStorageRate());
		entity.setEdge(vo.getEdge());
		checkMinMax(entity);

		saveOrUpdateSpec(entity, vo);

		// Update the unbound increment of the global quote
		quote.setUnboundCostCounter(quote.getUnboundCostCounter() + deltaUnbound);

		// Save and update the costs
		final var storagesCosts = new HashMap<Integer, Floating>();
		final var dirtyPrice = !oldLocation.equals(entity.getResolvedLocation());
		CollectionUtils.emptyIfNull(entity.getStorages()).stream().peek(s -> {
			if (dirtyPrice) {
				// Location has changed, the available storage price need a refresh
				storageResource.refresh(s);
				storageResource.refreshCost(s);
			}
		}).forEach(s -> storagesCosts.put(s.getId(), addCost(s, storageResource::updateCost)));
		final var cost = newUpdateCost(entity);
		cost.getRelated().put(ResourceType.STORAGE, storagesCosts);

		// Add tags
		super.saveOrUpdate(entity, vo);

		// Refresh costs
		if (BooleanUtils.isTrue(quote.getLeanOnChange())) {
			budgetResource.lean(entity.getResolvedBudget(), cost.getRelated());
			if (!Objects.equals(oldBudget, entity.getResolvedBudget())) {
				// Also update the old budget
				budgetResource.lean(oldBudget, cost.getRelated());
			}
		}
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Save or update the resource type specific properties.
	 *
	 * @param entity The entity to update.
	 * @param vo     The change to apply to the entity.
	 */
	protected abstract void saveOrUpdateSpec(final C entity, final E vo);

	private void checkMinMax(C entity) {
		if (entity.getMaxQuantity() != null && entity.getMaxQuantity() < entity.getMinQuantity()) {
			// Maximal quantity must be greater than minimal quantity
			throw new ValidationJsonException("maxQuantity", "Min", entity.getMinQuantity());
		}
	}

	@Override
	protected UpdatedCost deleteAll(final int subscription) {
		final var quote = resource.getQuoteFromSubscription(subscription);

		// Get all storage instances' identifiers associated to this ressource type
		final var sIds = ((BasePovInstanceBehavior) getQiRepository()).findAllStorageIdentifiers(quote);

		// Delete these associated storage instances
		((BasePovInstanceBehavior) getQiRepository()).deleteAllStorages(quote);

		// Notify this deletion to observers
		tagResource.onDelete(ResourceType.STORAGE, sIds.toArray(new Integer[0]));
		networkResource.onDelete(ResourceType.STORAGE, sIds.toArray(new Integer[0]));
		qsRepository.flush();

		// Delete all ressources of this type with cascade
		final var cost = super.deleteAll(subscription);

		// Recompute actual cost
		cost.getDeleted().put(ResourceType.STORAGE, sIds);
		budgetResource.lean(quote, cost.getRelated());
		return cost;
	}

	@Override
	protected UpdatedCost delete(final int id) {
		final var cost = new UpdatedCost(id);
		tagResource.onDelete(getType(), id);
		networkResource.onDelete(getType(), id);
		final var entity = deleteAndUpdateCost(getQiRepository(), id, i -> {
			// Delete the related storages
			i.getStorages().forEach(s -> {
				tagResource.onDelete(ResourceType.STORAGE, s.getId());
				networkResource.onDelete(ResourceType.STORAGE, s.getId());
				deleteAndUpdateCost(qsRepository, s.getId(), e -> cost.getDeleted()
						.computeIfAbsent(ResourceType.STORAGE, m -> new HashSet<>()).add(e.getId()));
			});

			// Decrement the unbound counter
			final var q = i.getConfiguration();
			q.setUnboundCostCounter(q.getUnboundCostCounter() - BooleanUtils.toInteger(i.isUnboundCost()));
		});

		// Prepare the updated cost of updated instances
		if (BooleanUtils.isTrue(entity.getConfiguration().getLeanOnChange())) {
			budgetResource.lean(entity.getResolvedBudget(), cost.getRelated());
		}
		return resource.refreshSupportCost(cost, entity);
	}

	/**
	 * Return the resolved usage entity from its name.
	 *
	 * @param configuration Configuration containing the default values and defined usages.
	 * @param name          The usage name to resolve.
	 * @return The resolved usage entity. Never <code>null</code> since the configuration's usage or else
	 * {@link #USAGE_DEFAULT} is used as default value.
	 */
	protected ProvUsage getUsage(final ProvQuote configuration, final String name) {
		return getProfileByName(configuration.getUsage(), name, configuration.getUsages(), USAGE_DEFAULT);
	}

	/**
	 * Return the resolved budget entity from its name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param name          The budget name to resolve.
	 * @return The resolved b entity. Never <code>null</code> since the configuration's budget or else
	 * {@link #BUDGET_DEFAULT} is used as default value.
	 */
	protected ProvBudget getBudget(final ProvQuote configuration, final String name) {
		return getProfileByName(configuration.getBudget(), name, configuration.getBudgets(), BUDGET_DEFAULT);
	}

	/**
	 * Return the resolved optimizer entity from its name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param name          The optimizer name to resolve.
	 * @return The resolved b entity. Never <code>null</code> since the configuration's optimizer or else
	 * {@link #OPTIMIZER_DEFAULT} is used as default value.
	 */
	protected ProvOptimizer getOptimizer(final ProvQuote configuration, final String name) {
		return getProfileByName(configuration.getOptimizer(), name, configuration.getOptimizers(), OPTIMIZER_DEFAULT);
	}

	/**
	 * Return the resolved profile entity from its name.
	 *
	 * @param quoteProfile   The global profile level value.
	 * @param name           The profile name to resolve.
	 * @param allProfiles    All defined profiles for this quote.
	 * @param defaultProfile Default profile.
	 * @return The resolved b entity. Never <code>null</code> since the configuration's profile or else the given
	 * default value.
	 */
	private <G extends INamableBean<?>> G getProfileByName(final G quoteProfile, final String name, List<G> allProfiles,
			G defaultProfile) {
		if (name == null) {
			return ObjectUtils.getIfNull(quoteProfile, defaultProfile);
		}
		return allProfiles.stream().filter(u -> u.getName().equals(name)).findFirst()
				.orElseThrow(() -> new EntityNotFoundException(name));
	}

	/**
	 * Return the resolved processor requirement.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param processor     The local processor requirement
	 * @return The resolved processor requirement. May be <code>null</code>.
	 */
	protected String getProcessor(final ProvQuote configuration, final String processor) {
		return ObjectUtils.getIfNull(processor, ObjectUtils.getIfNull(configuration.getProcessor(), ""));
	}

	/**
	 * Return the instance type identifier from its code.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param code         The type's code.May be <code>null</code>.
	 * @return The instance type identifier. Default is 0.
	 */
	protected int getType(final int subscription, final String code) {
		return code == null ? 0 : assertFound(getItRepository().findByCode(subscription, code), code).getId();
	}

	/**
	 * Return the adjusted required CPU depending on the configuration.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param qi            The query context.
	 * @return The adjusted required CPU depending on the configuration.
	 */
	protected double getCpu(final ProvQuote configuration, final QuoteVm qi) {
		return Math.max(0.5d, getReserved(configuration, qi.getCpu(), qi.getCpuMax()));
	}

	/**
	 * Return the adjusted required RAM from the original one and the RAM configuration.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param qi            The query context.
	 * @return The adjusted required RAM from the original one and the RAM configuration.
	 */
	protected double getRam(final ProvQuote configuration, final QuoteVm qi) {
		return Math.max(128, ObjectUtils.getIfNull(configuration.getRamAdjustedRate(), 100)
				* getReserved(configuration, qi.getRam(), qi.getRamMax()) / 100d);
	}

	/**
	 * Return the adjusted required GPU depending on the configuration.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param qi            The query context.
	 * @return The adjusted required GPU depending on the configuration.
	 */
	protected double getGpu(final ProvQuote configuration, final QuoteVm qi) {
		return Math.max(0d, getReserved(configuration, qi.getGpu(), qi.getGpuMax()));
	}

	/**
	 * Return the right resource value depending on the reservation mode.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param reserved      The reserved value.
	 * @param max           The maximal value.
	 * @param <N>           Number type.
	 * @return The right resource value depending on the reservation mode.
	 */
	protected <N extends Number> N getReserved(final ProvQuote configuration, N reserved, N max) {
		return configuration.getReservationMode() == ReservationMode.MAX && max != null ? max : reserved;
	}

	/**
	 * Return the location identifier from its name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param location      The location name. When <code>null</code>, the default one is used.
	 * @return The resolved location identifier from its name. Never <code>null</code>.
	 */
	protected int getLocation(final ProvQuote configuration, final String location) {
		final var provider = String.join(":",
				ArrayUtils.subarray(StringUtils.split(configuration.getSubscription().getNode().getId(), ':'), 0, 3));
		return location == null ? configuration.getLocation().getId()
				: assertFound(locationRepository.toId(provider, location), location).intValue();
	}

	/**
	 * Return the resource price type available for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param uriInfo      filter data.
	 * @return The available price types for the given subscription.
	 */
	protected TableItem<ProvInstancePriceTerm> findPriceTerms(final int subscription, @Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				iptRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo).toUpperCase(),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	@Override
	protected Floating getCost(final C qi) {
		return getCost(qi, qi.getPrice());
	}

	/**
	 * Return the computed cost from a resolved price.
	 *
	 * @param qi The query context.
	 * @param ip The resource price configuration.
	 * @return The updated cost of this resource.
	 */
	protected Floating getCost(final C qi, final P ip) {
		// Fixed price + custom price
		final double rate = getRate(qi, ip);
		final var workload = Workload.from(qi.getWorkload());
		return computeFloat(
				rate * (ip.getCost()
						+ (ip.getType().isCustom() ? getCustomCost(qi.getCpu(), qi.getGpu(), qi.getRam(), ip) : 0)),
				rate * getCo2(qi, ip, workload), ip.getInitialCost(), qi);
	}

	/**
	 * Return the rate associated to  the related term. When on demand, the effective usage is considered.
	 *
	 * @param qi The query context.
	 * @param ip The resource price configuration.
	 * @return The rate associated to  the related term.
	 */
	protected double getRate(final C qi, final P ip) {
		if (ip.getTerm().getPeriod() == 0) {
			// Related term has a period lesser than the month, rate can be applied
			return getRate(qi) / 100d;
		}
		return 1d;
	}

	private double getCo2Base10(final double co2b100, final String co2b10, final Workload workload) {
		final var baseline = workload.getBaseline();
		if (baseline < 100 && StringUtils.isNotEmpty(co2b10)) {
			final var co2Values = StringUtils.split(co2b10, ',');
			// At least one CO2 profile in addition of the full one
			final var step = 100d / co2Values.length;
			return workload.getPeriods().stream().mapToDouble(p -> {
				final var index = (int) (p.value / step);
				final var baselineMin = index * step;
				final var co2Min = index >= co2Values.length - 1 ? co2b100 : Double.parseDouble(co2Values[index]);
				final var co2Max = index >= co2Values.length - 2 ? co2b100 : Double.parseDouble(co2Values[index + 1]);
				return p.duration * (co2Min + (co2Max - co2Min) * ((p.value - baselineMin) / step)) / 100d;
			}).sum();
		}
		return co2b100;
	}

	/**
	 * Update the CO2 based on the baseline distributions.
	 */
	protected double getCo2(final C qi, final P ip, final Workload workload) {
		var co2 = 0d;
		co2 += getCo2Base10(ip.getCo2(), ip.getCo210(), workload);
		if (ip.getType().isCustom()) {
			final var qCpu = getQuantity(qi.getCpu(), ip.getMinCpu(), ip.getIncrementCpu(), 1);
			final var qRam = getQuantity(qi.getRam(), ip.getMinRam(), ip.getIncrementRam(), 1024);
			final var qGpu = getQuantity(qi.getGpu(), ip.getMinGpu(), ip.getIncrementGpu(), 1);
			co2 += getCo2Base10(ip.getCo2Cpu(), ip.getCo2Cpu10(), workload) * qCpu;
			co2 += getCo2Base10(ip.getCo2Ram(), ip.getCo2Ram10(), workload) * qRam;
			co2 += getCo2Base10(ip.getCo2Gpu(), ip.getCo2Gpu10(), workload) * qGpu;
		}
		return co2;
	}

	/**
	 * Return the rate from the given resource, using first the local, then the global, then the default rate.
	 *
	 * @param qi The resource to inspect.
	 * @return The effective rate.
	 */
	protected int getRate(final C qi) {
		return Optional.ofNullable(qi.getResolvedUsage()).map(ProvUsage::getRate).orElse(ProvUsage.MAX_RATE);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param cpu The requested CPU.
	 * @param gpu The requested CPU.
	 * @param ram The requested RAM in MB.
	 * @param ip  The resource price configuration.
	 * @return The cost of this custom resource.
	 */
	protected double getCustomCost(final double cpu, final double gpu, final int ram, final P ip) {
		// Compute the count of the requested resources
		return getCustomCost(cpu, ip.getCostCpu(), ip.getMinCpu(), ip.getIncrementCpu(), 1)
				+ getCustomCost(gpu, ip.getCostCpu(), ip.getMinGpu(), ip.getIncrementGpu(), 1)
				+ getCustomCost(ram, ip.getCostRam(), ip.getMinRam(), ip.getIncrementRam(), 1024);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param requested The request resource amount.
	 * @param cost      The cost of one resource.
	 * @param min       The minimum resource amount.
	 * @param increment The increment resource amount.
	 * @param weight    The weight of one resource.
	 * @return The cost of this custom instance resource.
	 */
	private double getCustomCost(final double requested, final Double cost, final Double min, final Double increment,
			final double weight) {
		// Compute the quantity of the requested resources and costs
		return getQuantity(requested, min, increment, weight) * Objects.requireNonNullElse(cost, 0d);
	}

	/**
	 * Compute the quantity of a custom requested resource.
	 *
	 * @param requested The request resource amount.
	 * @param min       The minimum resource amount.
	 * @param increment The increment resource amount.
	 * @param weight    The weight of one resource.
	 * @return The count of this custom instance resource.
	 */
	private double getQuantity(final double requested, final Double min, final Double increment, final double weight) {
		final double incrementD = Objects.requireNonNullElse(increment, 1d);
		// Compute the count of the requested resources
		return Math.ceil(Math.max(Math.ceil(requested / weight), Objects.requireNonNullElse(min, 0d)) / incrementD)
				* incrementD;
	}

	/**
	 * Compute the cost using minimal and maximal quantity of related resource. no rounding there.
	 *
	 * @param base    The cost of one resource.
	 * @param baseCo2 The CO2 consumption of one resource.
	 * @param initial The initial cost of one resource. May be <code>null</code>.
	 * @param qi      The quote resource to compute.
	 * @return The updated cost of this resource.
	 */
	public static Floating computeFloat(final double base, final double baseCo2, final Double initial,
			final AbstractQuoteVm<?> qi) {
		final var initialR = Objects.requireNonNullElse(initial, 0d);
		final var maxQuantity = Objects.requireNonNullElse(qi.getMaxQuantity(), qi.getMinQuantity());
		return new Floating(base * qi.getMinQuantity(), base * maxQuantity, initialR * qi.getMinQuantity(),
				initialR * maxQuantity, qi.isUnboundCost(), baseCo2 * qi.getMinQuantity(), baseCo2 * maxQuantity);
	}

	/**
	 * Request a cost update of the given entity and report the delta to the global cost. The changes are persisted.
	 *
	 * @param entity The quote resource to update.
	 * @return The new computed cost.
	 */
	private UpdatedCost newUpdateCost(final C entity) {
		return newUpdateCost(getQiRepository(), entity, this::updateCost);
	}

	/**
	 * Compute the right license value.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param license       The quote license value. May be <code>null</code>.
	 * @param canByol       The criteria used to evaluate the license <code>null</code> value.
	 * @return The human-readable license value.
	 */
	protected String getLicense(final ProvQuote configuration, final String license, final boolean canByol) {
		var licenseR = license;
		if (license == null && canByol) {
			// Dual license modes are managed only for WINDOWS OS for now
			licenseR = configuration.getLicense();
		}
		if (AbstractQuoteVm.LICENSE_INCLUDED.equalsIgnoreCase(licenseR)) {
			// Database handle included license as 'null'
			licenseR = null;
		} else if (licenseR != null) {
			licenseR = licenseR.toUpperCase(Locale.ENGLISH);
		}
		return licenseR;
	}

	/**
	 * Return a {@link QuoteInstanceLookup} corresponding to the best price.
	 *
	 * @param subscription The subscription identifier will be used to filter the instances from the associated
	 *                     provider.
	 * @param query        The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	public L lookupInternal(final int subscription, final Q query) {
		final var result = lookup(getQuoteFromSubscription(subscription), query);
		if (result != null) {
			// Fetch term and the type for serialization
			Hibernate.initialize(result.getPrice().getTerm());
			Hibernate.initialize(result.getPrice().getType());
		}
		return result;
	}

	/**
	 * Return a lookup research corresponding to the best price.
	 *
	 * @param configuration The subscription configuration.
	 * @param query         The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	public L lookup(final ProvQuote configuration, final Q query) {

		// Compute the rate to use
		final var usage = getUsage(configuration, query.getUsageName());
		final var rate = usage.getRate() / 100d;
		final var duration = usage.getDuration();
		final var maxPeriod = (int) Math.ceil(duration * rate) + 12;
		final var start = System.currentTimeMillis();
		var moreExecution = false;

		var lookup = this.lookup(configuration, query, maxPeriod, 10);
		if (lookup == null) {
			// Another wider lookup
			moreExecution = true;
			lookup = this.lookup(configuration, query, 10000, 10000);
		}
		// Return the match
		log.debug("lookup {} (ext={}): {}ms - {}", configuration.getSubscription().getId(), moreExecution,
				System.currentTimeMillis() - start, query);
		return lookup;
	}

	/**
	 * Return a lookup research corresponding to the best price.
	 *
	 * @param configuration The subscription configuration.
	 * @param query         The query parameters.
	 * @param maxPeriod     The maximal period to be queried in the valid terms.
	 * @param maxFactor     The maximal CPU and RAM factor to be queried in the valid instance types.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	private L lookup(final ProvQuote configuration, final Q query, final int maxPeriod, final double maxFactor) {
		final var node = configuration.getSubscription().getNode().getTool().getId();
		final int subscription = configuration.getSubscription().getId();
		final var ramR = getRam(configuration, query);
		final var cpuR = getCpu(configuration, query);
		final var gpuR = getGpu(configuration, query);
		final var procR = getProcessor(configuration, query.getProcessor());
		final var physR = normalize(configuration.getPhysical(), query.getPhysical());
		final var p1TypeOnly = normalize(configuration.getP1TypeOnly(), query.getP1TypeOnly());

		// Resolve the location to use
		final var locationR = getLocation(configuration, query.getLocationName());

		// Compute the rate to use
		final var usage = getUsage(configuration, query.getUsageName());
		final var budget = getBudget(configuration, query.getBudgetName());
		final var convOs = BooleanUtils.toBoolean(usage.getConvertibleOs());
		final var convEngine = BooleanUtils.toBoolean(usage.getConvertibleEngine());
		final var convType = BooleanUtils.toBoolean(usage.getConvertibleType());
		final var convFamily = BooleanUtils.toBoolean(usage.getConvertibleFamily());
		final var convLocation = BooleanUtils.toBoolean(usage.getConvertibleLocation());
		final var reservation = BooleanUtils.toBoolean(usage.getReservation());
		final var rate = usage.getRate() / 100d;
		final var workload = Workload.from(query.getWorkload());
		final var duration = usage.getDuration();
		final double initialCost = Objects.requireNonNullElse(budget.getRemainingBudget(), budget.getInitialCost());
		final var baseline = workload.getBaseline();
		final var baselineR = (double) Math.round(baseline / 5d) * 5; // Round for cache hit improvement

		// Override the optimizer depending on the capabilities of the catalog
		var optimizer = getOptimizer(configuration, query.getOptimizerName()).getMode();
		optimizer = (optimizer == Optimizer.CO2 && getItRepository().hasCo2Data(node)) ? optimizer : Optimizer.COST;

		// Resolve the required instance type
		final var typeId = getType(subscription, query.getType());
		final var types = getItRepository().findValidTypes(node, cpuR, gpuR, ramR, cpuR * maxFactor, gpuR * maxFactor,
				ramR * maxFactor, baselineR, physR, typeId, procR, query.isAutoScale(), normalize(query.getCpuRate()),
				normalize(query.getGpuRate()), normalize(query.getRamRate()), normalize(query.getNetworkRate()),
				normalize(query.getStorageRate()), normalize(query.getEdge()), optimizer == Optimizer.CO2);

		// Resolve the valid terms
		final var terms = iptRepository.findValidTerms(node,
				(getType() == ResourceType.INSTANCE || getType() == ResourceType.CONTAINER
						|| getType() == ResourceType.FUNCTION) && convOs,
				getType() == ResourceType.DATABASE && convEngine, convType, convFamily, convLocation, reservation,
				maxPeriod, query.isEphemeral(), initialCost > 0);
		Object[] lookup = null;

		// Find the best price
		if (!types.isEmpty()) {
			// Get the best template instance price
			lookup = findLowestPrice(configuration, query, types, terms, locationR, rate, duration, initialCost,
					optimizer, p1TypeOnly).stream().findFirst().orElse(null);
		}

		// Dynamic type lookup
		if (getItRepository().hasDynamicalTypes(node) && gpuR == 0) {
			final var dTypes = getItRepository().findDynamicTypes(node, baselineR, physR, typeId, procR,
					query.isAutoScale(), normalize(query.getCpuRate()), normalize(query.getGpuRate()),
					normalize(query.getRamRate()), normalize(query.getNetworkRate()), normalize(query.getStorageRate()),
					normalize(query.getEdge()), optimizer == Optimizer.CO2);
			if (!dTypes.isEmpty()) {
				// Get the best dynamic instance price
				var dLookup = findLowestDynamicPrice(configuration, query, dTypes, terms, cpuR, gpuR, ramR, locationR,
						rate, duration, initialCost, optimizer, p1TypeOnly).stream().findFirst().orElse(null);
				if (dLookup != null && lookup == null || (dLookup != null
						&& ((optimizer == Optimizer.COST && toTotalCost(dLookup) < toTotalCost(lookup))
						|| (optimizer == Optimizer.CO2 && toTotalCo2(dLookup) < toTotalCo2(lookup))))) {
					// Keep the best one
					lookup = dLookup;
				}
			}
		}

		// No result
		if (lookup == null) {
			return null;
		}

		// Return the best match
		return newPrice(lookup);
	}

	/**
	 * Build a new {@link AbstractLookup} from {@link ProvInstancePrice} and computed price.
	 *
	 * @param rs Raw result from the lookup.
	 * @return a new {@link AbstractLookup} instance.
	 */
	protected abstract L newPrice(final Object[] rs);

	/**
	 * Return the lowest price matching all requirements.
	 *
	 * @param configuration The subscription configuration.
	 * @param query         The query parameters.
	 * @param types         The valid types matching to the requirements.
	 * @param terms         The valid terms matching to the requirements.
	 * @param location      The required location.
	 * @param rate          Usage rate within the duration, from 0 (stopped) to 1 (full time).
	 * @param duration      The committed duration.
	 * @param initialCost   The maximal initial cost.
	 * @param optimizer     The optimizer mode.
	 * @param p1TypeOnly    P1 type only (latest available) is requested.
	 * @return The valid prices result.
	 */
	protected abstract List<Object[]> findLowestPrice(ProvQuote configuration, Q query, List<Integer> types,
			List<Integer> terms, int location, double rate, double duration, final double initialCost,
			final Optimizer optimizer, final boolean p1TypeOnly);

	/**
	 * Return the lowest price matching all requirements for dynamic types.
	 *
	 * @param configuration The subscription configuration.
	 * @param query         The query parameters.
	 * @param types         The valid dynamic types matching to the requirements.
	 * @param terms         The valid terms matching to the requirements.
	 * @param cpu           The required CPU.
	 * @param gpu           The required GPU.
	 * @param ram           The required RAM.
	 * @param location      The required location.
	 * @param rate          Usage rate within the duration, from 0 (stopped) to 1 (full time).
	 * @param duration      Committed duration.
	 * @param initialCost   The maximal initial cost.
	 * @param p1TypeOnly    P1 type only (latest available) is requested.
	 * @param optimizer     The optimizer mode.
	 * @return The valid prices result.
	 */
	protected abstract List<Object[]> findLowestDynamicPrice(ProvQuote configuration, Q query, List<Integer> types,
			List<Integer> terms, double cpu, double gpu, double ram, int location, double rate, int duration,
			double initialCost, final Optimizer optimizer, final boolean p1TypeOnly);

	@Override
	public Floating refresh(final C qi) {
		// Find the lowest price
		qi.setPrice(validateLookup(qi));
		return updateCost(qi);
	}

	/**
	 * Return the new cost corresponding to the given criteria. No changes are made to the entity.
	 *
	 * @param qi The entity to validate.
	 * @return The new cost corresponding to the given criteria. No changes are made to the entity.
	 */
	public FloatingPrice<P> getNewPrice(final C qi) {
		// Find the lowest price
		final var price = validateLookup(qi);
		return new FloatingPrice<>(getCost(qi, price), price);
	}

	/**
	 * Return a computed price. Never <code>null</code> because of the validation.
	 */
	@SuppressWarnings("unchecked")
	private P validateLookup(final C qi) {
		return validateLookup(getType().name().toLowerCase(), lookup(qi.getConfiguration(), (Q) qi), qi.getName());
	}

	/**
	 * Return the total cost from the query result.
	 *
	 * @param lookup The lookup result set.
	 * @return The cost value.
	 */
	protected double toTotalCost(final Object[] lookup) {
		return ((Double) lookup[1]);
	}

	/**
	 * Return the total co2 from the query result.
	 *
	 * @param lookup The lookup result set.
	 * @return The co2 value.
	 */
	protected double toTotalCo2(final Object[] lookup) {
		return ((Double) lookup[3]);
	}

	/**
	 * Return the tool provisioning node from the configuration entity.
	 *
	 * @param configuration The configuration entity attached to a node.
	 * @return The Spring component handling the tool provisioning node.
	 */
	protected ProvisioningService getService(final ProvQuote configuration) {
		return Objects.requireNonNullElseGet(configuration.getService(), () -> {
			configuration.setService(
					locator.getResource(configuration.getSubscription().getNode().getId(), ProvisioningService.class));
			return configuration.getService();
		});
	}

	/**
	 * Return the instance types inside available for the related catalog.
	 *
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @param uriInfo      filter data.
	 * @return The valid instance types for the given subscription.
	 */
	protected TableItem<T> findAllTypes(final int subscription, final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				getItRepository().findAll(subscription, DataTableAttributes.getSearch(uriInfo).toUpperCase(),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

}
