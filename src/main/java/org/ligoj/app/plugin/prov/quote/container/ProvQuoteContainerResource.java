/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.container;

import java.util.List;

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

import org.ligoj.app.plugin.prov.AbstractProvQuoteInstanceOsResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
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
			final int duration, final double initialCost) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		return ipRepository.findLowestPrice(types, terms, os, location, rate, duration, licenseR, initialCost,
				PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteContainer query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost) {
		final var service = getService(configuration);
		// Resolve the right OS
		final var os = service.getCatalogOs(query.getOs());
		// Resolve the right license model
		final var licenseR = normalize(getLicense(configuration, query.getLicense(), os, this::canByol));
		return ipRepository.findLowestDynamicPrice(types, terms, Math.ceil(Math.max(1, cpu)),
				Math.ceil(round(ram / 1024)), os, location, rate, round(rate * duration), duration, licenseR,
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
		return result;
	}
}
