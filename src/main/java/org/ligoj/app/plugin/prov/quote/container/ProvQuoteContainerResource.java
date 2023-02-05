/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.container;

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

import org.ligoj.app.plugin.prov.AbstractProvQuoteInstanceOsResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvContainerPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvContainerTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteContainerRepository;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.QuoteContainer;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * The container part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteContainerResource extends
		AbstractProvQuoteInstanceOsResource<ProvContainerType, ProvContainerPrice, ProvQuoteContainer, QuoteContainerEditionVo, QuoteContainerLookup, QuoteContainer> {

	@Getter
	@Autowired
	private ProvContainerPriceRepository ipRepository;

	@Getter
	@Autowired
	private ProvQuoteContainerRepository qiRepository;

	@Getter
	@Autowired
	private ProvContainerTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.CONTAINER;
	}

	@Override
	@POST
	@Path("container")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteContainerEditionVo vo) {
		return saveOrUpdate(new ProvQuoteContainer(), vo);
	}

	@Override
	@PUT
	@Path("container")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteContainerEditionVo vo) {
		return super.update(vo);
	}

	@Override
	@DELETE
	@Path("{subscription:\\d+}/container")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	@DELETE
	@Path("container/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	/**
	 * Return the container prices matching to the criteria.
	 *
	 * @param subscription The subscription identifier.
	 * @param query        The criteria.
	 * @return The best container price matching to the criteria.
	 */
	@GET
	@Path("{subscription:\\d+}/container-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteContainerLookup lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteContainerQuery query) {
		return lookupInternal(subscription, query);
	}

	@Override
	protected List<Object[]> findLowestPrice(final ProvQuote configuration, final QuoteContainer query,
			final List<Integer> types, final List<Integer> terms, final int location, final double rate,
			final double duration, final double initialCost, final Optimizer optimizer) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestCo2(types, terms, os, location, rate, duration, licenseR, initialCost,
					PageRequest.of(0, 1));
		}
		return ipRepository.findLowestCost(types, terms, os, location, rate, duration, licenseR, initialCost,
				PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteContainer query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		final var cpuR = Math.ceil(Math.max(1, cpu));
		final var ramR = Math.ceil(round(ram / 1024));
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestDynamicCo2(types, terms, cpuR,cpuR, gpu,gpu,
					ramR,ramR, os, location, rate, round(rate * duration), duration, licenseR,
					initialCost, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestDynamicCost(types, terms, cpuR,cpuR, gpu,gpu,
				ramR,ramR, os, location, rate, round(rate * duration), duration, licenseR,
				initialCost, PageRequest.of(0, 1));
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/container-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findPriceTerms(subscription, uriInfo);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/container-license/{os}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("os") final VmOs os) {
		return super.findLicenses(subscription, os);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/container-os")
	public List<String> findOs(@PathParam("subscription") final int subscription) {
		return super.findOs(subscription);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/container-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvContainerType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findAllTypes(subscription, uriInfo);
	}

	@Override
	protected QuoteContainerLookup newPrice(final Object[] rs) {
		final var result = new QuoteContainerLookup();
		result.setPrice((ProvContainerPrice) rs[0]);
		result.setCost(round((double) rs[2]));
		result.setCo2(round((double) rs[4]));
		return result;
	}

}
