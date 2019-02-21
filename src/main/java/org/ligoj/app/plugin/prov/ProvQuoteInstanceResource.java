/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.transaction.Transactional;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The instance part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvQuoteInstanceResource extends
		AbstractProvQuoteInstanceResource<ProvInstanceType, ProvInstancePrice, ProvQuoteInstance, QuoteInstanceEditionVo
		, QuoteInstanceLookup, QuoteInstance, QuoteInstanceQuery> {

	@Getter
	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Getter
	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Getter
	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.INSTANCE;
	}

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

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteInstance entity, final QuoteInstanceEditionVo vo) {
		entity.setOs(ObjectUtils.defaultIfNull(vo.getOs(), entity.getPrice().getOs()));
		entity.setEphemeral(vo.isEphemeral());
		entity.setMaxVariableCost(vo.getMaxVariableCost());
		entity.setInternet(vo.getInternet());
		entity.setSoftware(StringUtils.trimToNull(vo.getSoftware()));
		checkOs(entity);
	}

	/**
	 * Check the requested OS is compliant with the one of associated {@link ProvInstancePrice}
	 */
	protected void checkOs(final ProvQuoteInstance entity) {
		if (entity.getOs().toPricingOs() != entity.getPrice().getOs()) {
			// Incompatible, hack attempt?
			log.warn("Attempt to create an instance with an incompatible OS {} with catalog OS {}", entity.getOs(),
					entity.getPrice().getOs());
			throw new ValidationJsonException("os", "incompatible-os", entity.getPrice().getOs());
		}
	}

	@Override
	@DELETE
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	@DELETE
	@Path("instance/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	@GET
	@Path("{subscription:\\d+}/instance-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	@Override
	public QuoteInstanceLookup lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteInstanceQuery query) {
		return super.lookup(subscription, query);
	}

	@Override
	protected QuoteInstanceLookup lookup(final ProvQuote configuration, final QuoteInstance query) {
		final String node = configuration.getSubscription().getNode().getId();
		final int subscription = configuration.getSubscription().getId();
		final double ramR = getRam(configuration, query.getRam());

		// Resolve the location to use
		final int locationR = getLocation(configuration, query.getLocationName(), node);

		// Compute the rate to use
		final ProvUsage usage = getUsage(configuration, query.getUsageName());
		final double rate = usage.getRate() / 100d;
		final int duration = usage.getDuration();

		// Resolve the required instance type
		final Integer typeId = getType(query.getType(), subscription);

		// Resolve the right license model
		final VmOs os = Optional.ofNullable(query.getOs()).map(VmOs::toPricingOs).orElse(null);
		final String licenseR = getLicense(configuration, query.getLicense(), os, this::canByol);
		final String softwareR = StringUtils.trimToNull(query.getSoftware());

		// Return only the first matching instance
		return ipRepository
				.findLowestPrice(node, query.getCpu(), ramR, query.getConstant(), os, typeId, query.isEphemeral(),
						locationR, rate, duration, licenseR, softwareR, PageRequest.of(0, 1))
				.stream().findFirst().map(rs -> newPrice((ProvInstancePrice) rs[0], (double) rs[2])).orElse(null);
	}

	private boolean canByol(final VmOs os) {
		return os == VmOs.WINDOWS;
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/instance-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findPriceTerms(subscription, uriInfo);
	}

	/**
	 * Return the available instance licenses for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param os
	 *            The filtered OS.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-license/{os}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("os") final VmOs os) {
		final List<String> result = ipRepository
				.findAllLicenses(subscriptionResource.checkVisible(subscription).getNode().getId(), os);
		result.replaceAll(l -> StringUtils.defaultIfBlank(l, ProvQuoteInstance.LICENSE_INCLUDED));
		return result;
	}

	/**
	 * Return the available instance software for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param os
	 *            The filtered OS.
	 * @return The available softwares for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-software/{os}")
	public List<String> findSoftwares(@PathParam("subscription") final int subscription,
			@PathParam("os") final VmOs os) {
		return ipRepository.findAllSoftwares(subscriptionResource.checkVisible(subscription).getNode().getId(), os);
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
	@Path("{subscription:\\d+}/instance-type")
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

}
