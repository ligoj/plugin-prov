/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.function;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import lombok.Getter;
import org.apache.commons.lang3.time.DateUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteVmResource;
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvFunctionPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvFunctionTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteFunctionRepository;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The function part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Getter
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

	@Autowired
	private ProvFunctionPriceRepository ipRepository;

	@Autowired
	private ProvQuoteFunctionRepository qiRepository;

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
			final double duration, final double initialCost, final Optimizer optimizer, final boolean p1TypeOnly) {
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestCo2(types, terms, location, rate, duration, initialCost, query.getDuration(),
					PageRequest.of(0, 1));
		}
		return ipRepository.findLowestCost(types, terms, location, rate, duration, initialCost, query.getDuration(),
				PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteFunction query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer, final boolean p1TypeOnly) {
		var result1 = findLowestDynamicPrice(query, types, terms, cpu, ram, location, rate,
				duration, initialCost, optimizer, Math.floor(query.getConcurrency()),
				Math.floor(query.getConcurrency()));
		if (!result1.isEmpty() && query.getConcurrency() != Math.floor(query.getConcurrency())) {
			// Try the greater concurrency level and keeping the original concurrency assumption
			var result2 = findLowestDynamicPrice(query, types, terms, cpu, ram, location, rate,
					duration, initialCost, optimizer, query.getConcurrency(), Math.ceil(query.getConcurrency()));
			if (toTotalCost(result1.getFirst()) > toTotalCost(result2.getFirst())) {
				// The second concurrency configuration is cheaper
				return result2;
			}
		}
		return result1;
	}

	private List<Object[]> findLowestDynamicPrice(final QuoteFunction query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer, final double realConcurrency, final double reservedConcurrency) {
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestDynamicCo2(types, terms, Math.ceil(Math.max(1, cpu)),
					Math.max(1, ram) / 1024d, location, rate, round(rate * duration), duration, initialCost,
					query.getNbRequests(), realConcurrency, reservedConcurrency, query.getDuration(),
					CONCURRENCY_PER_MONTH, 1.0d, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestDynamicCost(types, terms, Math.ceil(Math.max(1, cpu)), Math.max(1, ram) / 1024d,
				location, rate, round(rate * duration), duration, initialCost, query.getNbRequests(), realConcurrency,
				reservedConcurrency, query.getDuration(), CONCURRENCY_PER_MONTH, 1.0d, PageRequest.of(0, 1));
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

	@Override
	protected Floating getCost(final ProvQuoteFunction qi, final ProvFunctionPrice ip) {
		// Fixed price
		final var baseCost = super.getCost(qi, ip).multiply(Math.max(1, qi.getConcurrency()));

		// Add per request cost
		final double rate = getRate(qi, ip);
		final var duration = Math.ceil(Math.max(ip.getMinDuration(), qi.getDuration()) / ip.getIncrementDuration()) * ip.getIncrementDuration();
		final var billedReqDuration = duration * qi.getNbRequests();
		final var costRamConcurrency = Math.min(qi.getConcurrency() * rate, billedReqDuration / CONCURRENCY_PER_MONTH) * ip.getCostRamRequestConcurrency();
		final var costRamRequest = Math.max(0, billedReqDuration / CONCURRENCY_PER_MONTH - qi.getConcurrency() * rate) * ip.getCostRamRequest();
		final var costRequest = ip.getCostRequests() * qi.getNbRequests();
		final var co2Request = ip.getCo2Requests() * qi.getNbRequests();

		// Sum costs
		return baseCost
				.add(computeFloat(costRamRequest, 0d, 0d, qi))
				.add(computeFloat(costRamConcurrency, 0d, 0d, qi))
				.add(computeFloat(costRequest, co2Request, 0d, qi));
	}

}
