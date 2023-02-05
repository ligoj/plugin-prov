/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.function;

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

import org.apache.commons.lang3.time.DateUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteVmResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvFunctionPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvFunctionTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteFunctionRepository;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
import org.ligoj.app.plugin.prov.model.QuoteFunction;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * The function part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteFunctionResource extends
		AbstractProvQuoteVmResource<ProvFunctionType, ProvFunctionPrice, ProvQuoteFunction, QuoteFunctionEditionVo, QuoteFunctionLookup, QuoteFunction> {

	/**
	 * Milliseconds per month.
	 */
	private static final double MILLIS_PER_MONTH = DateUtils.MILLIS_PER_HOUR
			* (double) ProvResource.DEFAULT_HOURS_MONTH /* Hours to month */;

	/**
	 * Milliseconds per month per million requests.
	 */
	private static final double CONCURRENCY_PER_MONTH = MILLIS_PER_MONTH / 1000000d /* Million requests */;

	@Getter
	@Autowired
	private ProvFunctionPriceRepository ipRepository;

	@Getter
	@Autowired
	private ProvQuoteFunctionRepository qiRepository;

	@Getter
	@Autowired
	private ProvFunctionTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.FUNCTION;
	}

	@Override
	@POST
	@Path("function")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteFunctionEditionVo vo) {
		return saveOrUpdate(new ProvQuoteFunction(), vo);
	}

	@Override
	@PUT
	@Path("function")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteFunctionEditionVo vo) {
		return super.update(vo);
	}

	@Override
	@DELETE
	@Path("{subscription:\\d+}/function")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	@DELETE
	@Path("function/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	/**
	 * Return the function prices matching to the criteria.
	 *
	 * @param subscription The subscription identifier.
	 * @param query        The criteria.
	 * @return The best function price matching to the criteria.
	 */
	@GET
	@Path("{subscription:\\d+}/function-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteFunctionLookup lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteFunctionQuery query) {
		return lookupInternal(subscription, query);
	}

	@Override
	protected List<Object[]> findLowestPrice(final ProvQuote configuration, final QuoteFunction query,
			final List<Integer> types, final List<Integer> terms, final int location, final double rate,
			final double duration, final double initialCost, final Optimizer optimizer) {
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestCo2(types, terms, location, rate, duration, initialCost, query.getDuration(), query.getDuration(),
					PageRequest.of(0, 1));
		}
		return ipRepository.findLowestCost(types, terms, location, rate, duration, initialCost, query.getDuration(), query.getDuration(),
				PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteFunction query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer) {
		var result1 = findLowestDynamicPrice(configuration, query, types, terms, cpu, gpu, ram, location, rate,
				duration, initialCost, optimizer, Math.floor(query.getConcurrency()),
				Math.floor(query.getConcurrency()));
		if (!result1.isEmpty() && query.getConcurrency() != Math.floor(query.getConcurrency())) {
			// Try the greater concurrency level and keeping the original concurrency assumption
			var result2 = findLowestDynamicPrice(configuration, query, types, terms, cpu, gpu, ram, location, rate,
					duration, initialCost, optimizer, query.getConcurrency(), Math.ceil(query.getConcurrency()));
			if (toTotalCost(result1.get(0)) > toTotalCost(result2.get(0))) {
				// The second concurrency configuration is cheaper
				return result2;
			}
		}
		return result1;
	}

	private List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteFunction query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer, final double realConcurrency, final double reservedConcurrency) {
		final var cpuR = Math.ceil(Math.max(1, cpu));
		final var ramR = Math.ceil(round(ram / 1024));
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestDynamicCo2(types, terms, cpuR, cpuR,cpuR,
					ramR,ramR, ramR, location, rate, round(rate * duration), duration, initialCost,
					query.getNbRequests(), realConcurrency, reservedConcurrency, reservedConcurrency, reservedConcurrency, query.getDuration(), query.getDuration(),
					CONCURRENCY_PER_MONTH, 1.0d, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestDynamicCost(types, terms, cpuR, cpuR,cpuR, ramR, ramR,ramR,
				location, rate, round(rate * duration), duration, initialCost, query.getNbRequests(), realConcurrency,
				reservedConcurrency, reservedConcurrency, reservedConcurrency, query.getDuration(), query.getDuration(), CONCURRENCY_PER_MONTH, 1.0d, PageRequest.of(0, 1));
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/function-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findPriceTerms(subscription, uriInfo);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/function-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvFunctionType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findAllTypes(subscription, uriInfo);
	}

	@Override
	protected QuoteFunctionLookup newPrice(final Object[] rs) {
		final var result = new QuoteFunctionLookup();
		result.setPrice((ProvFunctionPrice) rs[0]);
		result.setCost(round((double) rs[2]));
		result.setCo2(round((double) rs[4]));
		return result;
	}

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteFunction entity, final QuoteFunctionEditionVo vo) {
		entity.setRuntime(vo.getRuntime());
		entity.setConcurrency(vo.getConcurrency());
		entity.setDuration(vo.getDuration());
		entity.setNbRequests(vo.getNbRequests());
	}
}
