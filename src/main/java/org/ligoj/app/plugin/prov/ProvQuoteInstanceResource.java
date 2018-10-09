/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The instance part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvQuoteInstanceResource extends AbstractCostedResource<ProvQuoteInstance> {

	/**
	 * The default usage : 100% for 1 month.
	 */
	private static final ProvUsage USAGE_DEFAULT = new ProvUsage();

	@Autowired
	private ProvQuoteStorageResource storageResource;

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvInstancePriceTermRepository iptRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ProvUsageRepository usageRepository;

	/**
	 * Create the instance inside a quote.
	 *
	 * @param vo
	 *            The quote instance.
	 * @return The created instance cost details with identifier.
	 */
	@POST
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(new ProvQuoteInstance(), vo);
	}

	/**
	 * Update the instance inside a quote.
	 *
	 * @param vo
	 *            The quote instance to update.
	 * @return The new cost configuration.
	 */
	@PUT
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qiRepository, vo.getId()), vo);
	}

	/**
	 * Save or update the given entity from the {@link QuoteInstanceEditionVo}. The computed cost are recursively
	 * updated from the instance to the quote total cost.
	 */
	private UpdatedCost saveOrUpdate(final ProvQuoteInstance entity, final QuoteInstanceEditionVo vo) {
		// Compute the unbound cost delta
		final int deltaUnbound = BooleanUtils.toInteger(vo.getMaxQuantity() == null)
				- BooleanUtils.toInteger(entity.isUnboundCost());

		// Check the associations and copy attributes to the entity
		final ProvQuote configuration = getQuoteFromSubscription(vo.getSubscription());
		final Subscription subscription = configuration.getSubscription();
		final String providerId = subscription.getNode().getRefined().getId();
		DescribedBean.copy(vo, entity);
		entity.setConfiguration(configuration);
		final ProvLocation oldLocation = getLocation(entity);
		entity.setPrice(ipRepository.findOneExpected(vo.getPrice()));
		entity.setLocation(resource.findLocation(providerId, vo.getLocation()));
		entity.setUsage(Optional.ofNullable(vo.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription.getId())).orElse(null));
		entity.setOs(ObjectUtils.defaultIfNull(vo.getOs(), entity.getPrice().getOs()));
		entity.setRam(vo.getRam());
		entity.setCpu(vo.getCpu());
		entity.setConstant(vo.getConstant());
		entity.setEphemeral(vo.isEphemeral());
		entity.setInternet(vo.getInternet());
		entity.setMaxVariableCost(vo.getMaxVariableCost());
		entity.setMinQuantity(vo.getMinQuantity());
		entity.setMaxQuantity(vo.getMaxQuantity());

		resource.checkVisibility(entity.getPrice().getType(), providerId);
		checkConstraints(entity);
		checkOs(entity);

		// Update the unbound increment of the global quote
		configuration.setUnboundCostCounter(configuration.getUnboundCostCounter() + deltaUnbound);

		// Save and update the costs
		final UpdatedCost cost = newUpdateCost(entity);
		final Map<Integer, FloatingCost> storagesCosts = new HashMap<>();
		final boolean dirtyPrice = !oldLocation.equals(getLocation(entity));
		CollectionUtils.emptyIfNull(entity.getStorages()).stream().peek(s -> {
			if (dirtyPrice) {
				// Location has changed, the available storage price need a refresh
				storageResource.refresh(s);
				storageResource.refreshCost(s);
			}
		}).forEach(s -> storagesCosts.put(s.getId(), addCost(s, storageResource::updateCost)));
		cost.setRelatedCosts(storagesCosts);
		cost.setTotalCost(toFloatingCost(entity.getConfiguration()));
		return cost;
	}

	/**
	 * Check the requested OS is compliant with the one of associated {@link ProvInstancePrice}
	 */
	private void checkOs(ProvQuoteInstance entity) {
		if (entity.getOs().toPricingOs() != entity.getPrice().getOs()) {
			// Incompatible, hack attempt?
			log.warn("Attempt to create an instance with an incompatible OS {} with catalog OS {}", entity.getOs(),
					entity.getPrice().getOs());
			throw new ValidationJsonException("os", "incompatible-os", entity.getPrice().getOs());
		}
	}

	private void checkConstraints(ProvQuoteInstance entity) {
		if (entity.getMaxQuantity() != null && entity.getMaxQuantity() < entity.getMinQuantity()) {
			// Maximal quantity must be greater than minimal quantity
			throw new ValidationJsonException("maxQuantity", "Min", entity.getMinQuantity());
		}
	}

	/**
	 * Delete all instances from a quote. The total cost is updated.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost deleteAll(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisible(subscription);

		// Delete all instance with cascaded delete for storages
		qiRepository.deleteAll(qiRepository.findAllBy("configuration.subscription.id", subscription));

		// Update the cost. Note the effort could be reduced to a simple
		// subtract of instances cost and related storage costs
		return resource.updateCost(subscription);
	}

	@Override
	public FloatingCost refresh(final ProvQuoteInstance qi) {
		final ProvQuote quote = qi.getConfiguration();

		// Find the lowest price
		qi.setPrice(validateLookup("instance", lookup(quote, qi.getCpu(), qi.getRam(), qi.getConstant(), qi.getOs(),
				null, qi.isEphemeral(), getLocation(qi).getName(), getUsageName(qi)), qi.getName()));
		return updateCost(qi);
	}

	/**
	 * Return the effective usage applied to the given instance. May be <code>null</code>.
	 */
	private ProvUsage getUsage(final ProvQuoteInstance qi) {
		return qi.getUsage() == null ? qi.getConfiguration().getUsage() : qi.getUsage();
	}

	/**
	 * Return the usage name applied to the given instance. May be <code>null</code>.
	 */
	private String getUsageName(final ProvQuoteInstance qi) {
		final ProvUsage usage = getUsage(qi);
		return usage == null ? null : usage.getName();
	}

	/**
	 * Delete an instance from a quote. The total cost is updated.
	 *
	 * @param id
	 *            The {@link ProvQuoteInstance}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	@DELETE
	@Path("instance/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public FloatingCost delete(@PathParam("id") final int id) {
		// Delete the instance and also the attached storage
		return deleteAndUpdateCost(qiRepository, id, i -> {
			// Delete the relate storages
			i.getStorages().forEach(s -> deleteAndUpdateCost(qsRepository, s.getId(), Function.identity()::apply));

			// Decrement the unbound counter
			final ProvQuote configuration = i.getConfiguration();
			configuration.setUnboundCostCounter(
					configuration.getUnboundCostCounter() - BooleanUtils.toInteger(i.isUnboundCost()));
		});
	}

	/**
	 * Create the instance inside a quote.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param cpu
	 *            The amount of required CPU. Default is 1.
	 * @param ram
	 *            The amount of required RAM, in MB. Default is 1.
	 * @param constant
	 *            Optional constant CPU. When <code>false</code>, variable CPU is requested. When <code>true</code>
	 *            constant CPU is requested.
	 * @param os
	 *            The requested OS, default is "LINUX".
	 * @param type
	 *            Optional instance type name. May be <code>null</code>.
	 * @param ephemeral
	 *            Optional ephemeral constraint. When <code>false</code> (default), only non ephemeral instance are
	 *            accepted. Otherwise (<code>true</code>), ephemeral instance contract is accepted.
	 * @param location
	 *            Optional location name. May be <code>null</code>.
	 * @param usage
	 *            Optional usage name. May be <code>null</code>.
	 * @return The lowest price instance configurations matching to the required parameters. May be a template or a
	 *         custom instance type.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteInstanceLookup lookup(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("cpu") final double cpu,
			@DefaultValue(value = "1") @QueryParam("ram") final int ram, @QueryParam("constant") final Boolean constant,
			@DefaultValue(value = "LINUX") @QueryParam("os") final VmOs os, @QueryParam("type") final String type,
			@QueryParam("ephemeral") final boolean ephemeral, @QueryParam("location") final String location,
			@QueryParam("usage") final String usage) {
		// Check the security on this subscription
		return lookup(getQuoteFromSubscription(subscription), cpu, ram, constant, os, type, ephemeral, location, usage);
	}

	private QuoteInstanceLookup lookup(final ProvQuote configuration, final double cpu, final int ram,
			final Boolean constant, final VmOs osName, final String type, final boolean ephemeral,
			final String location, final String usageName) {
		final String node = configuration.getSubscription().getNode().getId();
		final int subscription = configuration.getSubscription().getId();

		// Resolve
		final VmOs os = Optional.ofNullable(osName).map(VmOs::toPricingOs).orElse(null);

		// Resolve the location to use
		final int locationR = location == null ? configuration.getLocation().getId()
				: assertFound(locationRepository.toId(node, location), location).intValue();

		// Compute the rate to use
		final ProvUsage usage = getUsage(configuration, usageName);
		final double rate = usage.getRate() / 100d;
		final int duration = usage.getDuration();

		// Resolve the required instance type
		final Integer typeId = type == null ? null
				: assertFound(itRepository.findByName(subscription, type), type).getId();

		// Return only the first matching instance
		// Template instance
		final QuoteInstanceLookup template = ipRepository
				.findLowestPrice(node, cpu, ram, constant, os, typeId, ephemeral, locationR, rate, duration,
						PageRequest.of(0, 1))
				.stream().findFirst().map(rs -> newPrice((ProvInstancePrice) rs[0], (double) rs[2])).orElse(null);

		// Custom instance
		final QuoteInstanceLookup custom = ipRepository
				.findLowestCustomPrice(node, Math.ceil(cpu), Math.round(ram / 1024), constant, os, locationR,
						PageRequest.of(0, 1))
				.stream().findFirst().map(rs -> newPrice((ProvInstancePrice) rs[0], rate * (double) rs[1]))
				.orElse(null);

		// Select the best price term
		if (template == null) {
			return custom;
		}
		if (custom == null) {
			return template;
		}
		return custom.getCost() < template.getCost() ? custom : template;
	}

	private ProvUsage getUsage(final ProvQuote configuration, final String name) {
		return Optional.ofNullable(name)
				.map(n -> resource.findConfiguredByName(usageRepository, n, configuration.getSubscription().getId()))
				.orElseGet(() -> ObjectUtils.defaultIfNull(configuration.getUsage(), USAGE_DEFAULT));
	}

	/**
	 * Return the instance price type available for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available price types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerm(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				iptRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the instance types inside available for the related catalog.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The valid instance types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstanceType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				itRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and computed price.
	 */
	private QuoteInstanceLookup newPrice(final ProvInstancePrice ip, final double cost) {
		final QuoteInstanceLookup result = new QuoteInstanceLookup();
		result.setCost(round(cost));
		result.setPrice(ip);
		return result;
	}

	@Override
	protected FloatingCost getCost(final ProvQuoteInstance qi) {
		// Fixed price + custom price
		final ProvInstancePrice ip = qi.getPrice();
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

	private int getRate(final ProvQuoteInstance qi) {
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
	 *            The instance price configuration.
	 * @return The cost of this custom instance.
	 */
	private double getCustomCost(final Double cpu, final Integer ram, final ProvInstancePrice ip) {
		// Compute the count of the requested resources
		return getCustomCost(cpu, ip.getCostCpu(), 1) + getCustomCost(ram, ip.getCostRam(), 1024);
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
	private double getCustomCost(Number requested, Double cost, final double weight) {
		// Compute the count of the requested resources
		return Math.ceil(requested.doubleValue() / weight) * cost;
	}

	/**
	 * Compute the cost using minimal and maximal quantity of related instance. no rounding there.
	 *
	 * @param base
	 *            The cost of one instance.
	 * @param qi
	 *            The quote instance to compute.
	 * @return The updated cost of this instance.
	 */
	public FloatingCost computeFloat(final double base, final ProvQuoteInstance qi) {
		final FloatingCost cost = new FloatingCost();
		cost.setMin(base * qi.getMinQuantity());
		cost.setMax(Optional.ofNullable(qi.getMaxQuantity()).orElse(qi.getMinQuantity()) * base);
		cost.setUnbound(qi.isUnboundCost());
		return cost;
	}

	/**
	 * Request a cost update of the given entity and report the delta to the the global cost. The changes are persisted.
	 *
	 * @param entity
	 *            The quote instance to update.
	 * @return The new computed cost.
	 */
	public UpdatedCost newUpdateCost(final ProvQuoteInstance entity) {
		return newUpdateCost(qiRepository, entity, this::updateCost);
	}

}
