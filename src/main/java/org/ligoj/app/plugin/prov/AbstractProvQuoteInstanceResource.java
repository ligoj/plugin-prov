/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.BasePovInstanceBehavior;
import org.ligoj.app.plugin.prov.dao.BaseProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteResourceRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvTermPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResourceInstance;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.QuoteVm;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The resource part of the provisioning.
 *
 * @param <T>
 *            The instance resource type.
 * @param <C>
 *            Quoted resource type.
 * @param <P>
 *            Quoted resource price type.
 */
public abstract class AbstractProvQuoteInstanceResource<T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteResourceInstance<P>, E extends AbstractQuoteInstanceEditionVo, L extends AbstractLookup<P>, Q extends QuoteVm, I extends AbstractQuoteInstanceQuery>
		extends AbstractCostedResource<T, P, C> {

	/**
	 * The default usage : 100% for 1 month.
	 */
	protected static final ProvUsage USAGE_DEFAULT = new ProvUsage();

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
	protected abstract BaseProvQuoteResourceRepository<C> getQiRepository();

	/**
	 * Return the repository managing the instance type entities.
	 *
	 * @return The repository managing the instance type entities.
	 */
	protected abstract BaseProvInstanceTypeRepository<T> getItRepository();

	/**
	 * Save or update the given entity from the {@link AbstractQuoteResourceInstance}. The computed cost are recursively
	 * updated from the resource to the quote total cost.
	 *
	 * @param entity
	 *            The entity to update.
	 * @param vo
	 *            The change to apply to the entity.
	 * @return The updated cost including the related ones.
	 */
	protected UpdatedCost saveOrUpdate(final C entity, final E vo) {
		// Compute the unbound cost delta
		final int deltaUnbound = BooleanUtils.toInteger(vo.getMaxQuantity() == null)
				- BooleanUtils.toInteger(entity.isUnboundCost());

		// Check the associations and copy attributes to the entity
		final ProvQuote quote = getQuoteFromSubscription(vo.getSubscription());
		final Subscription subscription = quote.getSubscription();
		final String providerId = subscription.getNode().getRefined().getId();
		DescribedBean.copy(vo, entity);
		entity.setConfiguration(quote);
		final ProvLocation oldLocation = entity.getResolvedLocation();
		entity.setPrice(getIpRepository().findOneExpected(vo.getPrice()));
		entity.setLocation(resource.findLocation(providerId, vo.getLocation()));
		entity.setUsage(Optional.ofNullable(vo.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription.getId())).orElse(null));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		entity.setConstant(vo.getConstant());
		entity.setMinQuantity(vo.getMinQuantity());
		entity.setMaxQuantity(vo.getMaxQuantity());
		entity.setLicense(Optional.ofNullable(vo.getLicense()).map(StringUtils::upperCase).orElse(null));
		resource.checkVisibility(entity.getPrice().getType(), providerId);
		checkMinMax(entity);

		saveOrUpdateSpec(entity, vo);

		// Update the unbound increment of the global quote
		quote.setUnboundCostCounter(quote.getUnboundCostCounter() + deltaUnbound);

		// Save and update the costs
		final Map<Integer, FloatingCost> storagesCosts = new HashMap<>();
		final boolean dirtyPrice = !oldLocation.equals(entity.getResolvedLocation());
		CollectionUtils.emptyIfNull(entity.getStorages()).stream().peek(s -> {
			if (dirtyPrice) {
				// Location has changed, the available storage price need a refresh
				storageResource.refresh(s);
				storageResource.refreshCost(s);
			}
		}).forEach(s -> storagesCosts.put(s.getId(), addCost(s, storageResource::updateCost)));
		final UpdatedCost cost = newUpdateCost(entity);
		cost.getRelated().put(ResourceType.STORAGE, storagesCosts);
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Save or update the resource type specific properties.
	 *
	 * @param entity
	 *            The entity to update.
	 * @param vo
	 *            The change to apply to the entity.
	 * @return The updated cost including the related ones.
	 */
	protected abstract void saveOrUpdateSpec(final C entity, final E vo);

	private void checkMinMax(C entity) {
		if (entity.getMaxQuantity() != null && entity.getMaxQuantity() < entity.getMinQuantity()) {
			// Maximal quantity must be greater than minimal quantity
			throw new ValidationJsonException("maxQuantity", "Min", entity.getMinQuantity());
		}
	}

	/**
	 * Delete all resource types from a quote. The total cost is updated.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @return The updated computed cost.
	 */
	protected UpdatedCost deleteAll(final int subscription) {
		final ProvQuote quote = resource.getQuoteFromSubscription(subscription);
		final UpdatedCost cost = new UpdatedCost(0);
		cost.getDeleted().put(getType(), getQiRepository().findAllIdentifiers(subscription));
		cost.getDeleted().put(ResourceType.STORAGE,
				((BasePovInstanceBehavior) getQiRepository()).findAllStorageIdentifiers(subscription));

		// Delete all resources with cascaded delete for storages
		((BasePovInstanceBehavior) getQiRepository()).deleteAllStorages(subscription);
		getQiRepository().deleteAllBySubscription(subscription);

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of resources cost and related storage costs
		resource.updateCost(subscription);
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Delete an resource from a quote. The total cost is updated.
	 *
	 * @param id
	 *            The {@link AbstractQuoteResourceInstance}'s identifier to delete.
	 * @return The updated computed cost. The main deleted resource is not listed itself in the updated cost.
	 */
	protected UpdatedCost delete(final int id) {
		final UpdatedCost cost = new UpdatedCost(id);
		return resource.refreshSupportCost(cost, deleteAndUpdateCost(getQiRepository(), id, i -> {
			// Delete the related storages
			i.getStorages().forEach(s -> deleteAndUpdateCost(qsRepository, s.getId(),
					e -> cost.getDeleted().computeIfAbsent(ResourceType.STORAGE, m -> new HashSet<>()).add(e.getId())));

			// Decrement the unbound counter
			final ProvQuote q = i.getConfiguration();
			q.setUnboundCostCounter(q.getUnboundCostCounter() - BooleanUtils.toInteger(i.isUnboundCost()));
		}));
	}

	/**
	 * Return the resolved usage entity from it's name.
	 *
	 * @param configuration
	 *            Configuration containing the default values.
	 * @param name
	 *            The usage name.
	 * @return The resolved usage entity. Never <code>null</code> since the configurtion's usage or else
	 *         {@link #USAGE_DEFAULT} is used as default value.
	 */
	protected ProvUsage getUsage(final ProvQuote configuration, final String name) {
		return Optional.ofNullable(name)
				.map(n -> resource.findConfiguredByName(usageRepository, n, configuration.getSubscription().getId()))
				.orElseGet(() -> ObjectUtils.defaultIfNull(configuration.getUsage(), USAGE_DEFAULT));
	}

	/**
	 * Return the resource type managed by this service.
	 *
	 * @return The resource type managed by this service.
	 */
	protected abstract ResourceType getType();

	/**
	 * Return the instance type identifier.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the resources from the associated provider.
	 * @param type
	 *            The type name.May be <code>null</code>.
	 * @return The instance type identifier. Will be <code>null</code> only when the given name was <code>null</code>
	 *         too.
	 */
	protected Integer getType(final int subscription, final String type) {
		return type == null ? null : assertFound(getItRepository().findByName(subscription, type), type).getId();
	}

	/**
	 * Return the adjusted required RAM from the original one and the RAM configuration.
	 *
	 * @param configuration
	 *            Configuration containing the default values.
	 * @param ram
	 *            The required RAM.
	 * @return The adjusted required RAM from the original one and the RAM configuration.
	 */
	protected double getRam(final ProvQuote configuration, final long ram) {
		return Math.round(ObjectUtils.defaultIfNull(configuration.getRamAdjustedRate(), 100) * ram / 100d);
	}

	/**
	 *
	 * Return the location identifier from it's name.
	 *
	 * @param configuration
	 *            Configuration containing the default values.
	 * @param location
	 *            The location name. When <code>null</code>, the default one is used.
	 * @return The resolved location identifier from it's name. Never <code>null</code>.
	 */
	protected int getLocation(final ProvQuote configuration, final String location) {
		return location == null ? configuration.getLocation().getId()
				: assertFound(locationRepository.toId(configuration.getSubscription().getNode().getId(), location),
						location).intValue();
	}

	/**
	 * Return the resource price type available for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the resources from the associated provider.
	 * @param uriInfo
	 *            filter data.
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
		// Fixed price + custom price
		final P ip = qi.getPrice();
		final double rate;
		if (ip.getTerm().getPeriod() == 0) {
			// Related term has a period lesser than the month, rate applies
			rate = getRate(qi) / 100d;
		} else {
			rate = 1d;
		}
		return computeFloat(
				rate * (ip.getCost() + (ip.getType().isCustom() ? getCustomCost(qi.getCpu(), qi.getRam(), ip) : 0)),
				qi);
	}

	private int getRate(final C qi) {
		return Optional.ofNullable(qi.getResolvedUsage()).map(ProvUsage::getRate).orElse(100);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param cpu
	 *            The requested CPU.
	 * @param ram
	 *            The requested RAM.
	 * @param ip
	 *            The resource price configuration.
	 * @return The cost of this custom resource.
	 */
	protected double getCustomCost(final Double cpu, final Integer ram, final P ip) {
		// Compute the count of the requested resources
		return getCustomCost(cpu, ((ProvInstancePrice) ip).getCostCpu(), 1)
				+ getCustomCost(ram, ((ProvInstancePrice) ip).getCostRam(), 1024);
	}

	/**
	 * Compute the monthly cost of a custom requested resource.
	 *
	 * @param requested
	 *            The request resource amount.
	 * @param cost
	 *            The cost of one resource.
	 * @param weight
	 *            The weight of one resource.
	 * @return The cost of this custom instance.
	 */
	private double getCustomCost(final Number requested, final Double cost, final double weight) {
		// Compute the count of the requested resources
		return Math.ceil(requested.doubleValue() / weight) * cost;
	}

	/**
	 * Compute the cost using minimal and maximal quantity of related resource. no rounding there.
	 *
	 * @param base
	 *            The cost of one resource.
	 * @param qi
	 *            The quote resource to compute.
	 * @return The updated cost of this resource.
	 */
	public static FloatingCost computeFloat(final double base, final AbstractQuoteResourceInstance<?> qi) {
		return new FloatingCost(base * qi.getMinQuantity(),
				Optional.ofNullable(qi.getMaxQuantity()).orElse(qi.getMinQuantity()) * base, qi.isUnboundCost());
	}

	/**
	 * Request a cost update of the given entity and report the delta to the the global cost. The changes are persisted.
	 *
	 * @param entity
	 *            The quote resource to update.
	 * @return The new computed cost.
	 */
	private UpdatedCost newUpdateCost(final C entity) {
		return newUpdateCost(getQiRepository(), entity, this::updateCost);
	}

	/**
	 * Compute the right license value.
	 *
	 * @param configuration
	 *            Configuration containing the default values.
	 * @param license
	 *            The quote license value. May be <code>null</code>.
	 * @param key
	 *            The criteria used to evaluate the license <code>null</code> value.
	 * @param canByol
	 *            The predicate evaluating the key when the given license is <code>null</code>
	 * @return The human readable license value.
	 */
	protected <K> String getLicense(final ProvQuote configuration, final String license, final K key,
			Predicate<K> canByol) {
		String licenseR = license;
		if (license == null && canByol.test(key)) {
			// Dual license modes are managed only for WINDOWS OS for now
			licenseR = configuration.getLicense();
		}
		if (ProvQuoteInstance.LICENSE_INCLUDED.equalsIgnoreCase(licenseR)) {
			// Database handle included license as 'null'
			licenseR = null;
		} else if (licenseR != null) {
			licenseR = licenseR.toUpperCase(Locale.ENGLISH);
		}
		return licenseR;
	}

	/**
	 * Create the instance inside a quote.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param query
	 *            The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	@SuppressWarnings("unchecked")
	public L lookup(int subscription, I query) {
		return lookup(subscription, (Q) query);
	}

	/**
	 * Return a {@link QuoteInstanceLookup} corresponding to the best price.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param query
	 *            The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	protected L lookup(final int subscription, final Q query) {
		return lookup(getQuoteFromSubscription(subscription), query);
	}

	/**
	 * Return a lookup research corresponding to the best price.
	 *
	 * @param configuration
	 *            The subscription configuration.
	 * @param query
	 *            The query parameters.
	 * @return The lowest price matching to the required parameters. May be <code>null</code>.
	 */
	protected abstract L lookup(final ProvQuote configuration, final Q query);

	@SuppressWarnings("unchecked")
	@Override
	public FloatingCost refresh(final C qi) {
		// Find the lowest price
		qi.setPrice(
				validateLookup(getType().name().toLowerCase(), lookup(qi.getConfiguration(), (Q) qi), qi.getName()));
		return updateCost(qi);
	}
}
