/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.instance;

import java.util.List;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteInstanceOsResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * The instance part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Getter
public class ProvQuoteInstanceResource extends
		AbstractProvQuoteInstanceOsResource<ProvInstanceType, ProvInstancePrice, ProvQuoteInstance, QuoteInstanceEditionVo, QuoteInstanceLookup, QuoteInstance> {

	@Autowired
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.INSTANCE;
	}

	@Override
	@POST
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteInstanceEditionVo vo) {
		return saveOrUpdate(new ProvQuoteInstance(), vo);
	}

	@Override
	@PUT
	@Path("instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteInstanceEditionVo vo) {
		return super.update(vo);
	}

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteInstance entity, final QuoteInstanceEditionVo vo) {
		entity.setSoftware(StringUtils.trimToNull(vo.getSoftware()));
		entity.setTenancy(ObjectUtils.getIfNull(vo.getTenancy(), ProvTenancy.SHARED));
		super.saveOrUpdateSpec(entity, vo);
		checkOs(entity);
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

	/**
	 * Return the instance prices matching to the criteria.
	 *
	 * @param subscription The subscription identifier.
	 * @param query        The criteria.
	 * @return The best instance price matching to the criteria.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteInstanceLookup lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteInstanceQuery query) {
		return lookupInternal(subscription, query);
	}

	@Override
	protected List<Object[]> findLowestPrice(final ProvQuote configuration, final QuoteInstance query,
			final List<Integer> types, final List<Integer> terms, final int location, final double rate,
			final double duration, final double initialCost, final Optimizer optimizer, final boolean p1TypeOnly) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		final var softwareR = normalize(query.getSoftware());
		final var tenancyR = ObjectUtils.getIfNull(query.getTenancy(), ProvTenancy.SHARED);
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestCo2(types, terms, os, location, rate, duration, licenseR, softwareR,
					initialCost, tenancyR, p1TypeOnly, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestCost(types, terms, os, location, rate, duration, licenseR, softwareR, initialCost,
				tenancyR, p1TypeOnly, PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteInstance query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer, final boolean p1TypeOnly) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		final var softwareR = normalize(query.getSoftware());
		final var tenancyR = ObjectUtils.getIfNull(query.getTenancy(), ProvTenancy.SHARED);
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestDynamicCo2(types, terms, Math.ceil(Math.max(1, cpu)), gpu,
					Math.ceil(round(ram / 1024)), os, location, rate, round(rate * duration), duration, licenseR,
					softwareR, initialCost, tenancyR, p1TypeOnly, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestDynamicCost(types, terms, Math.ceil(Math.max(1, cpu)), gpu,
				Math.ceil(round(ram / 1024)), os, location, rate, round(rate * duration), duration, licenseR, softwareR,
				initialCost, tenancyR, p1TypeOnly, PageRequest.of(0, 1));
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/instance-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findPriceTerms(subscription, uriInfo);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/instance-license/{os}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("os") final VmOs os) {
		return super.findLicenses(subscription, os);
	}

	/**
	 * Return the available instance software for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @param os           The filtered OS.
	 * @return The available software names for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-software/{os}")
	public List<String> findSoftwareNames(@PathParam("subscription") final int subscription,
			@PathParam("os") final VmOs os) {
		return ipRepository
				.findAllSoftwareNames(subscriptionResource.checkVisible(subscription).getNode().getTool().getId(), os);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/instance-os")
	public List<String> findOs(@PathParam("subscription") final int subscription) {
		return super.findOs(subscription);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/instance-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstanceType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findAllTypes(subscription, uriInfo);
	}

	@Override
	protected QuoteInstanceLookup newPrice(final Object[] rs) {
		final var result = new QuoteInstanceLookup();
		result.setPrice((ProvInstancePrice) rs[0]);
		result.setCost(round((double) rs[2]));
		result.setCo2(round((double) rs[4]));
		return result;
	}

}
