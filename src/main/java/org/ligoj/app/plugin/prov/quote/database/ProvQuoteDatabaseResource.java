/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.database;

import java.util.List;
import java.util.Objects;
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

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.QuoteDatabase;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceLookup;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
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
public class ProvQuoteDatabaseResource extends
		AbstractProvQuoteInstanceResource<ProvDatabaseType, ProvDatabasePrice, ProvQuoteDatabase, QuoteDatabaseEditionVo, QuoteDatabaseLookup, QuoteDatabase> {

	private static final String ENGINE_ORACLE = "ORACLE";

	@Getter
	@Autowired
	private ProvDatabasePriceRepository ipRepository;

	@Getter
	@Autowired
	private ProvQuoteDatabaseRepository qiRepository;

	@Getter
	@Autowired
	private ProvDatabaseTypeRepository itRepository;

	@Override
	protected ResourceType getType() {
		return ResourceType.DATABASE;
	}

	/**
	 * Create the database inside a quote.
	 *
	 * @param vo The quote instance.
	 * @return The created instance cost details with identifier.
	 */
	@POST
	@Path("database")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteDatabaseEditionVo vo) {
		return saveOrUpdate(new ProvQuoteDatabase(), vo);
	}

	/**
	 * Update the database inside a quote.
	 *
	 * @param vo The quote instance to update.
	 * @return The new cost configuration.
	 */
	@PUT
	@Path("database")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteDatabaseEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qiRepository, vo.getId()), vo);
	}

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteDatabase entity, final QuoteDatabaseEditionVo vo) {
		entity.setEngine(vo.getEngine());
		entity.setEdition(vo.getEdition());
		checkAttribute("engine", entity.getPrice().getEngine(), entity.getEngine());
		checkAttribute("edition", entity.getPrice().getEdition(), entity.getEdition());
	}

	/**
	 * Check the requested edition is compliant with the one of associated
	 * {@link ProvDatabasePrice}
	 *
	 * @param name   The attribute to check.
	 * @param pQuote The quote required attribute value.
	 * @param vPrice The price attribute value.
	 * @param <V>    The quote property value type.
	 */
	protected <V> void checkAttribute(final String name, final V pQuote, final V vPrice) {
		if (!Objects.equals(pQuote, vPrice)) {
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
	protected QuoteDatabaseLookup lookup(final ProvQuote configuration, final QuoteDatabase query) {
		final var node = configuration.getSubscription().getNode().getId();
		final int subscription = configuration.getSubscription().getId();
		final var ramR = (int) getRam(configuration, query.getRam());

		// Resolve the location to use
		final var locationR = getLocation(configuration, query.getLocationName());

		// Compute the rate to use
		configuration.getUsages().size();
		final var usage = getUsage(configuration, query.getUsageName());
		final var rate = usage.getRate() / 100d;
		final var duration = usage.getDuration();

		// Resolve the required instance type
		final var typeId = getType(subscription, query.getType());

		// Resolve the right license model
		final var licenseR = getLicense(configuration, query.getLicense(), query.getEngine(), this::canByol);
		final var editionR = normalize(query.getEdition());
		final var engineR = normalize(query.getEngine());

		// Return only the first matching instance
		return ipRepository
				.findLowestPrice(node, query.getCpu(), ramR, query.getConstant(), typeId, locationR, rate, duration,
						licenseR, engineR, editionR, PageRequest.of(0, 1))
				.stream().findFirst().map(rs -> newPrice((ProvDatabasePrice) rs[0], (double) rs[2])).orElse(null);
	}

	private String normalize(final String value) {
		return StringUtils.trimToNull(StringUtils.upperCase(value));
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
	 * @param subscription The subscription identifier, will be used to filter the
	 *                     instances from the associated provider.
	 * @param engine       The filtered engine.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-license/{engine}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		final var result = ipRepository
				.findAllLicenses(subscriptionResource.checkVisible(subscription).getNode().getId(), normalize(engine));
		result.replaceAll(l -> StringUtils.defaultIfBlank(l, ProvQuoteInstance.LICENSE_INCLUDED));
		return result;
	}

	/**
	 * Return the available database engines for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the
	 *                     instances from the associated provider.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-engine")
	public List<String> findEngines(@PathParam("subscription") final int subscription) {
		return ipRepository.findAllEngines(subscriptionResource.checkVisible(subscription).getNode().getId());
	}

	/**
	 * Return the available database edition software for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the
	 *                     instances from the associated provider.
	 * @param engine       The filtered engine.
	 * @return The available softwares for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-edition/{engine}")
	public List<String> findEditions(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		return ipRepository.findAllEditions(subscriptionResource.checkVisible(subscription).getNode().getId(),
				normalize(engine));
	}

	/**
	 * Return the instance types inside available for the related catalog.
	 *
	 * @param subscription The subscription identifier, will be used to filter the
	 *                     instances from the associated provider.
	 * @param uriInfo      filter data.
	 * @return The valid instance types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvDatabaseType> findAllTypes(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				itRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and
	 * computed price.
	 */
	private QuoteDatabaseLookup newPrice(final ProvDatabasePrice ip, final double cost) {
		final var result = new QuoteDatabaseLookup();
		result.setCost(round(cost));
		result.setPrice(ip);
		return result;
	}

}
