/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.persistence.EntityNotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.dao.BasePovInstanceBehavior;
import org.ligoj.app.plugin.prov.dao.BaseProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvTermPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.QuoteVm;
import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The resource part of the provisioning.
 *
 * @param <T> The instance resource type.
 * @param <P> Quoted resource price type.
 * @param <C> Quoted resource type.
 * @param <E> Quoted resource edition VO type.
 * @param <L> Quoted resource lookup result type.
 * @param <Q> Quoted resource details type.
 */
public abstract class AbstractProvQuoteInstanceResource<T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteVm<P>, E extends AbstractQuoteInstanceEditionVo, L extends AbstractLookup<P>, Q extends QuoteVm>
		extends AbstractProvQuoteResource<T, P, C> {

	/**
	 * The default usage : 100% for 1 month.
	 */
	protected static final ProvUsage USAGE_DEFAULT = new ProvUsage();

	/**
	 * The default budget : no initial cost.
	 */
	protected static final ProvBudget BUDGET_DEFAULT = new ProvBudget();

	@Autowired
	protected ProvQuoteStorageResource storageResource;

	@Autowired
	protected ProvInstancePriceTermRepository iptRepository;

	@Autowired
	protected ProvLocationRepository locationRepository;

	@Autowired
	protected ProvQuoteStorageRepository qsRepository;

	@Autowired
	protected ProvUsageRepository usageRepository;

	@Autowired
	protected ProvBudgetResource budgetRepsource;

	@Autowired
	protected ServicePluginLocator locator;

	/**
	 * Return the repository managing the instance pricing entities.
	 *
	 * @return The repository managing the instance pricing entities.
	 */
	protected abstract BaseProvTermPriceRepository<T, P> getIpRepository();

	/**
	 * Return the repository managing the quote entities.
	 *
	 * @return The repository managing the quote entities.
	 */
	protected abstract BaseProvQuoteRepository<C> getQiRepository();

	/**
	 * Return the repository managing the instance type entities.
	 *
	 * @return The repository managing the instance type entities.
	 */
	protected abstract BaseProvInstanceTypeRepository<T> getItRepository();

	@Override
	protected BaseProvQuoteRepository<C> getResourceRepository() {
		return getQiRepository();
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
	 * the resource to the quote total cost. The change must contains a price retrieved from a previous lookup. The
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
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		entity.setProcessor(vo.getProcessor());
		entity.setConstant(vo.getConstant());
		entity.setPhysical(vo.getPhysical());
		entity.setMinQuantity(vo.getMinQuantity());
		entity.setMaxQuantity(vo.getMaxQuantity());
		entity.setLicense(Optional.ofNullable(vo.getLicense()).map(StringUtils::upperCase).orElse(null));
		entity.setRamMax(vo.getRamMax());
		entity.setCpuMax(vo.getCpuMax());
		entity.setAutoScale(vo.isAutoScale());
		entity.setRamRate(vo.getRamRate());
		entity.setCpuRate(vo.getCpuRate());
		entity.setNetworkRate(vo.getNetworkRate());
		entity.setStorageRate(vo.getStorageRate());
		checkMinMax(entity);

		saveOrUpdateSpec(entity, vo);

		// Update the unbound increment of the global quote
		quote.setUnboundCostCounter(quote.getUnboundCostCounter() + deltaUnbound);

		// Save and update the costs
		final var storagesCosts = new HashMap<Integer, FloatingCost>();
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
			budgetRepsource.lean(entity.getResolvedBudget(), cost.getRelated());
			if (!Objects.equals(oldBudget, entity.getResolvedBudget())) {
				// Also update the old budget
				budgetRepsource.lean(oldBudget, cost.getRelated());
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
		// Delete all resources with cascaded delete for storages
		final var sIds = ((BasePovInstanceBehavior) getQiRepository()).findAllStorageIdentifiers(quote);
		((BasePovInstanceBehavior) getQiRepository()).deleteAllStorages(quote);
		tagResource.onDelete(ResourceType.STORAGE, sIds.toArray(new Integer[0]));
		networkResource.onDelete(ResourceType.STORAGE, sIds.toArray(new Integer[0]));
		qsRepository.flush();

		final var cost = super.deleteAll(subscription);
		cost.getDeleted().put(ResourceType.STORAGE, sIds);
		budgetRepsource.lean(quote, cost.getRelated());
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
			budgetRepsource.lean(entity.getResolvedBudget(), cost.getRelated());
		}
		return resource.refreshSupportCost(cost, entity);
	}

	/**
	 * Return the resolved usage entity from it's name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param name          The usage name.
	 * @return The resolved usage entity. Never <code>null</code> since the configurtion's usage or else
	 *         {@link #USAGE_DEFAULT} is used as default value.
	 */
	protected ProvUsage getUsage(final ProvQuote configuration, final String name) {
		if (name == null) {
			return ObjectUtils.defaultIfNull(configuration.getUsage(), USAGE_DEFAULT);
		}
		return configuration.getUsages().stream().filter(u -> u.getName().equals(name)).findFirst()
				.orElseThrow(() -> new EntityNotFoundException(name));
	}

	/**
	 * Return the resolved budget entity from it's name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param name          The usage name.
	 * @return The resolved usage entity. Never <code>null</code> since the configurtion's budget or else
	 *         {@link #BUDGET_DEFAULT} is used as default value.
	 */
	protected ProvBudget getBudget(final ProvQuote configuration, final String name) {
		if (name == null) {
			return ObjectUtils.defaultIfNull(configuration.getBudget(), BUDGET_DEFAULT);
		}
		return configuration.getBudgets().stream().filter(u -> u.getName().equals(name)).findFirst()
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
		return ObjectUtils.defaultIfNull(processor, configuration.getProcessor());
	}

	/**
	 * Return the resolved resource requirement from the resource or from the quote.
	 *
	 * @param quoteValue Quote's value.
	 * @param value      The local requirement value.
	 * @return The resolved requirement. May be <code>null</code>.
	 */
	protected Boolean getBoolean(final Boolean quoteValue, final Boolean value) {
		return ObjectUtils.defaultIfNull(value, quoteValue);
	}

	/**
	 * Return the instance type identifier.
	 *
	 * @param subscription The subscription identifier, will be used to filter the resources from the associated
	 *                     provider.
	 * @param type         The type's code.May be <code>null</code>.
	 * @return The instance type identifier. Will be <code>null</code> only when the given name was <code>null</code>
	 *         too.
	 */
	protected Integer getType(final int subscription, final String type) {
		return type == null ? null : assertFound(getItRepository().findByCode(subscription, type), type).getId();
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
		return Math.max(128, Math.round(ObjectUtils.defaultIfNull(configuration.getRamAdjustedRate(), 100))
				* getReserved(configuration, qi.getRam(), qi.getRamMax()) / 100d);
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
	 * Return the location identifier from it's name.
	 *
	 * @param configuration Configuration containing the default values.
	 * @param location      The location name. When <code>null</code>, the default one is used.
	 * @return The resolved location identifier from it's name. Never <code>null</code>.
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
				iptRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	@Override
	protected FloatingCost getCost(final C qi) {
		return getCost(qi, qi.getPrice());
	}

	/**
	 * Return the computed cost from a resolved price.
	 */
	private FloatingCost getCost(final C qi, final P ip) {
		// Fixed price + custom price
		final double rate;
		if (ip.getTerm().getPeriod() == 0) {
			// Related term has a period lesser than the month, rate applies
			rate = getRate(qi) / 100d;
		} else {
			rate = 1d;
		}
		return computeFloat(
				rate * (ip.getCost() + (ip.getType().isCustom() ? getCustomCost(qi.getCpu(), qi.getRam(), ip) : 0)),
				ip.getInitialCost(), qi);
	}

	private int getRate(final C qi) {
		return Optional.ofNullable(qi.getResolvedUsage()).map(ProvUsage::getRate).orElse(100);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param cpu The requested CPU.
	 * @param ram The requested RAM in MB.
	 * @param ip  The resource price configuration.
	 * @return The cost of this custom resource.
	 */
	protected double getCustomCost(final Double cpu, final Integer ram, final P ip) {
		// Compute the count of the requested resources
		return getCustomCost(
				Math.round(Math.ceil(Math.max(cpu, ip.getMinCpu()) / ip.getIncrementCpu()) * ip.getIncrementCpu()),
				ip.getCostCpu(), 1) + getCustomCost(ram, ip.getCostRam(), 1024);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param requested The request resource amount.
	 * @param cost      The cost of one resource.
	 * @param weight    The weight of one resource.
	 * @return The cost of this custom instance resource.
	 */
	private double getCustomCost(final Number requested, final double cost, final double weight) {
		// Compute the count of the requested resources
		return Math.ceil(requested.doubleValue() / weight) * cost;
	}

	/**
	 * Compute the cost using minimal and maximal quantity of related resource. no rounding there.
	 *
	 * @param base    The cost of one resource.
	 * @param initial The initial cost of one resource. May be <code>null</code>.
	 * @param qi      The quote resource to compute.
	 * @return The updated cost of this resource.
	 */
	public static FloatingCost computeFloat(final double base, final Double initial, final AbstractQuoteVm<?> qi) {
		final var initialR = Objects.requireNonNullElse(initial, 0d);
		final var maxQuantity = Objects.requireNonNullElse(qi.getMaxQuantity(), qi.getMinQuantity());
		return new FloatingCost(base * qi.getMinQuantity(), base * maxQuantity, initialR * qi.getMinQuantity(),
				initialR * maxQuantity, qi.isUnboundCost());
	}

	/**
	 * Request a cost update of the given entity and report the delta to the the global cost. The changes are persisted.
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
	 * @param key           The criteria used to evaluate the license <code>null</code> value.
	 * @param canByol       The predicate evaluating the key when the given license is <code>null</code>
	 * @param <K>           The key type.
	 * @return The human readable license value.
	 */
	protected <K> String getLicense(final ProvQuote configuration, final String license, final K key,
			Predicate<K> canByol) {
		var licenseR = license;
		if (license == null && canByol.test(key)) {
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
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @param query        The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	public L lookupInternal(final int subscription, final Q query) {
		final var result = lookup(getQuoteFromSubscription(subscription), query);
		if (result != null) {
			// Fetch term and the type for serialization
			result.getPrice().getTerm();
			result.getPrice().getType();
		}
		return result;
	}

	/**
	 * Return the managed resource type.
	 *
	 * @return The managed resource type.
	 */
	protected abstract ResourceType getResourceType();

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
		var lookup = this.lookup(configuration, query, maxPeriod, 10);
		if (lookup == null) {
			// Another wider lookup
			lookup = this.lookup(configuration, query, 10000, 10000);
		}
		// Return the match
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
		final var node = configuration.getSubscription().getNode().getId();
		final int subscription = configuration.getSubscription().getId();
		final var ramR = getRam(configuration, query);
		final var cpuR = getCpu(configuration, query);
		final var procR = getProcessor(configuration, query.getProcessor());
		final var physR = getBoolean(configuration.getPhysical(), query.getPhysical());

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
		final var duration = usage.getDuration();
		final var initialCost = Objects.requireNonNullElse(budget.getRemainingBudget(), budget.getInitialCost());

		// Resolve the required instance type
		final var typeId = getType(subscription, query.getType());
		final var types = getItRepository().findValidTypes(node, cpuR, ramR, cpuR * maxFactor,
				 ramR * maxFactor, query.getConstant(), physR, typeId, procR, query.isAutoScale(),
				query.getCpuRate(), query.getRamRate(), query.getNetworkRate(), query.getStorageRate());
		final var terms = iptRepository.findValidTerms(node, getResourceType() == ResourceType.INSTANCE && convOs,
				getResourceType() == ResourceType.DATABASE && convEngine, convType, convFamily, convLocation,
				reservation, maxPeriod, query.isEphemeral(), locationR, initialCost > 0);
		Object[] lookup = null;
		if (!types.isEmpty()) {
			// Get the best template instance price
			lookup = findLowestPrice(configuration, query, types, terms, locationR, rate, duration, initialCost)
					.stream().findFirst().orElse(null);
		}

		// Dynamic type test
		if (getItRepository().hasDynamicalTypes(node)) {
			final var dTypes = getItRepository().findDynamicTypes(node, query.getConstant(), physR, typeId, procR,
					query.isAutoScale(), query.getCpuRate(), query.getRamRate(), query.getNetworkRate(),
					query.getStorageRate());
			if (!dTypes.isEmpty()) {
				// Get the best dynamic instance price
				var dlookup = findLowestDynamicPrice(configuration, query, dTypes, terms, cpuR, ramR, locationR, rate,
						duration, initialCost).stream().findFirst().orElse(null);
				if (lookup == null || dlookup != null && toTotalCost(dlookup) < toTotalCost(lookup)) {
					// Keep the best one
					lookup = dlookup;
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
	 * @param rate          The usage rate.
	 * @param duration      The committed duration.
	 * @param initialCost   The maximal initial cost.
	 * @return The valid prices result.
	 */
	protected abstract List<Object[]> findLowestPrice(ProvQuote configuration, Q query, List<Integer> types,
			List<Integer> terms, int location, double rate, int duration, final double initialCost);

	/**
	 * Return the lowest price matching all requirements for dynamic types.
	 *
	 * @param configuration The subscription configuration.
	 * @param query         The query parameters.
	 * @param types         The valid dynamic types matching to the requirements.
	 * @param terms         The valid terms matching to the requirements.
	 * @param cpu           The required CPU.
	 * @param ram           The required RAM.
	 * @param location      The required location.
	 * @param rate          The usage rate.
	 * @param duration      The committed duration.
	 * @param initialCost   The maximal initial cost.
	 * @return The valid prices result.
	 */
	protected abstract List<Object[]> findLowestDynamicPrice(ProvQuote configuration, Q query, List<Integer> types,
			List<Integer> terms, double cpu, double ram, int location, double rate, int duration, double initialCost);

	@Override
	public FloatingCost refresh(final C qi) {
		// Find the lowest price
		qi.setPrice(validateLookup(qi));
		return updateCost(qi);
	}

	/**
	 * Return the new cost corresponding to the given criteria. No change are made to then entity.
	 * 
	 * @param qi The entity to validate.
	 * @return The new cost corresponding to the given criteria. No change are made to then entity.
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
	 * Return a normalized form a string.
	 *
	 * @param value The raw value.
	 * @return The normalized value.
	 */
	protected String normalize(final String value) {
		return StringUtils.trimToNull(StringUtils.upperCase(value));
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
}
