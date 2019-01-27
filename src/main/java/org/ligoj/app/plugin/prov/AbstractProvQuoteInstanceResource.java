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

import javax.ws.rs.PathParam;
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
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The resource part of the provisioning.
 */
public abstract class AbstractProvQuoteInstanceResource<T extends AbstractInstanceType, P extends AbstractTermPrice<T>, C extends AbstractQuoteResourceInstance<P>, E extends AbstractQuoteInstanceEditionVo>
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

	protected abstract BaseProvTermPriceRepository<T, P> getIpRepository();

	protected abstract BaseProvQuoteResourceRepository<C> getQiRepository();

	protected abstract BaseProvInstanceTypeRepository<T> getItRepository();

	/**
	 * Return the resource type managed by this service.
	 *
	 * @return The resource type managed by this service.
	 */
	protected abstract ResourceType getType();

	/**
	 * Save or update the given entity from the {@link AbstractQuoteResourceInstance}. The computed cost are recursively
	 * updated from the resource to the quote total cost.
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
		final ProvLocation oldLocation = getLocation(entity);
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
		final boolean dirtyPrice = !oldLocation.equals(getLocation(entity));
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
	protected UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		final ProvQuote quote = resource.getQuoteFromSubscription(subscription);
		final UpdatedCost cost = new UpdatedCost(0);
		cost.getDeleted().put(getType(), getQiRepository().findAllIdentifiers(subscription));
		cost.getDeleted().put(ResourceType.STORAGE, ((BasePovInstanceBehavior)getQiRepository()).findAllStorageIdentifiers(subscription));

		// Delete all resources with cascaded delete for storages
		((BasePovInstanceBehavior)getQiRepository()).deleteAllStorages(subscription);
		getQiRepository().deleteAllBySubscription(subscription);

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of resources cost and related storage costs
		resource.updateCost(subscription);
		return resource.refreshSupportCost(cost, quote);
	}

	/**
	 * Return the effective usage applied to the given resource. May be <code>null</code>.
	 */
	private ProvUsage getUsage(final C qi) {
		return qi.getUsage() == null ? qi.getConfiguration().getUsage() : qi.getUsage();
	}

	/**
	 * Return the usage name applied to the given resource. May be <code>null</code>.
	 */
	protected String getUsageName(final C qi) {
		final ProvUsage usage = getUsage(qi);
		return usage == null ? null : usage.getName();
	}

	/**
	 * Delete an resource from a quote. The total cost is updated.
	 *
	 * @param id
	 *            The {@link AbstractQuoteResourceInstance}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	protected UpdatedCost delete(@PathParam("id") final int id) {
		final UpdatedCost cost = new UpdatedCost(id);
		return resource.refreshSupportCost(cost, deleteAndUpdateCost(getQiRepository(), id, i -> {
			// Delete the relate storages
			i.getStorages().forEach(s -> deleteAndUpdateCost(qsRepository, s.getId(),
					e -> cost.getDeleted().computeIfAbsent(ResourceType.STORAGE, m -> new HashSet<>()).add(e.getId())));

			// Decrement the unbound counter
			final ProvQuote q = i.getConfiguration();
			q.setUnboundCostCounter(q.getUnboundCostCounter() - BooleanUtils.toInteger(i.isUnboundCost()));
		}));
	}

	protected ProvUsage getUsage(final ProvQuote configuration, final String name) {
		return Optional.ofNullable(name)
				.map(n -> resource.findConfiguredByName(usageRepository, n, configuration.getSubscription().getId()))
				.orElseGet(() -> ObjectUtils.defaultIfNull(configuration.getUsage(), USAGE_DEFAULT));
	}

	protected Integer getType(final String type, final int subscription) {
		return type == null ? null : assertFound(getItRepository().findByName(subscription, type), type).getId();
	}

	protected double getRam(final ProvQuote configuration, final long ram) {
		return Math.round(ObjectUtils.defaultIfNull(configuration.getRamAdjustedRate(), 100) * ram / 100d);
	}

	protected int getLocation(final ProvQuote configuration, final String location, final String node) {
		return location == null ? configuration.getLocation().getId()
				: assertFound(locationRepository.toId(node, location), location).intValue();
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
	protected TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
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
		return Optional.ofNullable(getUsage(qi)).map(ProvUsage::getRate).orElse(100);
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
	protected abstract double getCustomCost(final Double cpu, final Integer ram, final P ip);

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
	 */
	protected <K> String getLicense(final ProvQuote configuration, final String license, final K key,
			Predicate<K> canByol) {
		String licenseR = license;
		if (license == null) {
			// Dual license modes are managed only for WINDOWS OS for now
			licenseR = canByol.test(key) ? configuration.getLicense() : null;
		}
		if (ProvQuoteInstance.LICENSE_INCLUDED.equalsIgnoreCase(licenseR)) {
			// Database handle included license as 'null'
			licenseR = null;
		}
		if (licenseR != null) {
			licenseR = licenseR.toUpperCase(Locale.ENGLISH);
		}
		return licenseR;
	}
}
