/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.database;

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

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteVmResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.Optimizer;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.QuoteDatabase;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * The database instance part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
@Getter
public class ProvQuoteDatabaseResource extends
		AbstractProvQuoteVmResource<ProvDatabaseType, ProvDatabasePrice, ProvQuoteDatabase, QuoteDatabaseEditionVo, QuoteDatabaseLookup, QuoteDatabase> {

	private static final String ENGINE_ORACLE = "ORACLE";

	@Autowired
	private ProvDatabasePriceRepository ipRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qiRepository;

	@Autowired
	private ProvDatabaseTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.DATABASE;
	}

	@Override
	@POST
	@Path("database")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteDatabaseEditionVo vo) {
		return saveOrUpdate(new ProvQuoteDatabase(), vo);
	}

	@Override
	@PUT
	@Path("database")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteDatabaseEditionVo vo) {
		return super.update(vo);
	}

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteDatabase entity, final QuoteDatabaseEditionVo vo) {
		checkAttribute("engine", entity.getPrice().getEngine(), vo.getEngine());
		checkAttribute("edition", entity.getPrice().getEdition(), vo.getEdition());
		entity.setEngine(entity.getPrice().getEngine());
		entity.setEdition(entity.getPrice().getEdition());
	}

	/**
	 * Check the requested edition is compliant with the one of associated {@link ProvDatabasePrice}
	 *
	 * @param name   The attribute to check.
	 * @param pQuote The quote required attribute value.
	 * @param vPrice The price attribute value.
	 */
	protected void checkAttribute(final String name, final String pQuote, final String vPrice) {
		if (!StringUtils.equalsIgnoreCase(pQuote, vPrice)) {
			// Incompatible, hack attempt?
			log.warn("Attempt to create a database with an incompatible {} {} with catalog {} {}", name, pQuote, name,
					vPrice);
			throw new ValidationJsonException(name, "incompatible-" + name, String.valueOf(vPrice));
		}
	}

	@Override
	@DELETE
	@Path("{subscription:\\d+}/database")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	@DELETE
	@Path("database/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	/**
	 * Return the database prices matching to the criteria.
	 *
	 * @param subscription The subscription identifier.
	 * @param query        The criteria.
	 * @return The best database price matching to the criteria.
	 */
	@GET
	@Path("{subscription:\\d+}/database-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteDatabaseLookup lookup(@PathParam("subscription") final int subscription,
			@BeanParam final QuoteDatabaseQuery query) {
		return lookupInternal(subscription, query);
	}

	@Override
	protected List<Object[]> findLowestPrice(final ProvQuote configuration, final QuoteDatabase query,
			final List<Integer> types, final List<Integer> terms, final int location, final double rate,
			final double duration, final double initialCost, final Optimizer optimizer) {
		// Resolve the right license model
		final var licenseR = getLicense(configuration, query.getLicense(), query.getEngine(), this::canByol);
		final var engineR = normalize(query.getEngine());
		final var editionR = normalize(query.getEdition());
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestCo2(types, terms, location, rate, duration, licenseR, engineR, editionR,
					initialCost, PageRequest.of(0, 1));
		}
		return ipRepository.findLowestCost(types, terms, location, rate, duration, licenseR, engineR, editionR,
				initialCost, PageRequest.of(0, 1));
	}

	@Override
	protected List<Object[]> findLowestDynamicPrice(final ProvQuote configuration, final QuoteDatabase query,
			final List<Integer> types, final List<Integer> terms, final double cpu, final double gpu, final double ram,
			final int location, final double rate, final int duration, final double initialCost,
			final Optimizer optimizer) {
		final var licenseR = getLicense(configuration, query.getLicense(), query.getEngine(), this::canByol);
		final var engineR = normalize(query.getEngine());
		final var editionR = normalize(query.getEdition());
		if (optimizer == Optimizer.CO2) {
			return ipRepository.findLowestDynamicCo2(types, terms, Math.ceil(cpu), gpu, Math.ceil(round(ram / 1024)),
					engineR, editionR, location, rate, round(rate * duration), duration, licenseR, initialCost,
					PageRequest.of(0, 1));
		}
		return ipRepository.findLowestDynamicCost(types, terms, Math.ceil(cpu), gpu, Math.ceil(round(ram / 1024)),
				engineR, editionR, location, rate, round(rate * duration), duration, licenseR, initialCost,
				PageRequest.of(0, 1));
	}

	private boolean canByol(final String engine) {
		return ENGINE_ORACLE.equalsIgnoreCase(engine);
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/database-price-term")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvInstancePriceTerm> findPriceTerms(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findPriceTerms(subscription, uriInfo);
	}

	/**
	 * Return the available instance licenses for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @param engine       The filtered engine.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-license/{engine}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		final var result = ipRepository.findAllLicenses(
				subscriptionResource.checkVisible(subscription).getNode().getTool().getId(), normalize(engine));
		result.replaceAll(l -> StringUtils.defaultIfBlank(l, AbstractQuoteVm.LICENSE_INCLUDED));
		return result;
	}

	/**
	 * Return the available database engines for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-engine")
	public List<String> findEngines(@PathParam("subscription") final int subscription) {
		return ipRepository.findAllEngines(subscriptionResource.checkVisible(subscription).getNode().getTool().getId());
	}

	/**
	 * Return the available database edition software for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the instances from the associated
	 *                     provider.
	 * @param engine       The filtered engine.
	 * @return The available software names for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-edition/{engine}")
	public List<String> findEditions(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		return ipRepository.findAllEditions(subscriptionResource.checkVisible(subscription).getNode().getTool().getId(),
				normalize(engine));
	}

	@Override
	@GET
	@Path("{subscription:\\d+}/database-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvDatabaseType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		return super.findAllTypes(subscription, uriInfo);
	}

	@Override
	protected QuoteDatabaseLookup newPrice(final Object[] rs) {
		final var result = new QuoteDatabaseLookup();
		result.setPrice((ProvDatabasePrice) rs[0]);
		result.setCost(round((double) rs[2]));
		result.setCo2(round((double) rs[4]));
		return result;
	}

}
