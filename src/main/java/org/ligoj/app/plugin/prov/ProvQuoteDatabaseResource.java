/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.util.List;
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

import org.apache.commons.lang3.StringUtils;
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
import org.ligoj.app.plugin.prov.model.ProvUsage;
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
		AbstractProvQuoteInstanceResource<ProvDatabaseType, ProvDatabasePrice, ProvQuoteDatabase, QuoteDatabaseEditionVo> {

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
	public UpdatedCost create(final QuoteDatabaseEditionVo vo) {
		return saveOrUpdate(new ProvQuoteDatabase(), vo);
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
	public UpdatedCost update(final QuoteDatabaseEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qiRepository, vo.getId()), vo);
	}

	@Override
	protected void saveOrUpdateSpec(final ProvQuoteDatabase entity, final QuoteDatabaseEditionVo vo) {
		entity.setEngine(vo.getEngine());
		entity.setEdition(vo.getEdition());
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

	/**
	 * Delete all instances from a quote. The total cost is updated.
	 *
	 * @param subscription
	 *            The related subscription.
	 * @return The updated computed cost.
	 */
	@Override
	@DELETE
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	public FloatingCost refresh(final ProvQuoteDatabase qi) {
		// Find the lowest price
		qi.setPrice(validateLookup("database",
				lookup(qi.getConfiguration(), qi.getCpu(), qi.getRam(), qi.getConstant(), null,
						getLocation(qi).getName(), getUsageName(qi), qi.getLicense(), qi.getEngine(), qi.getEdition()),
				qi.getName()));
		return updateCost(qi);
	}

	/**
	 * Delete an instance from a quote. The total cost is updated.
	 *
	 * @param id
	 *            The {@link ProvQuoteInstance}'s identifier to delete.
	 * @return The updated computed cost.
	 */
	@Override
	@DELETE
	@Path("instance/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
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
	 *            Optional location name. When <code>null</code>, the global quote's location is used.
	 * @param usage
	 *            Optional usage name. May be <code>null</code> to use the default one.
	 * @param license
	 *            Optional license model. When <code>null</code>, the global quote's license is used.
	 * @param software
	 *            Optional built-in software. May be <code>null</code>. When not <code>null</code> a software constraint
	 *            is added. WHen <code>null</code>, installed software is also accepted.
	 * @return The lowest price instance configurations matching to the required parameters. May be a template or a
	 *         custom instance type.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public QuoteDatabaseLookup lookup(@PathParam("subscription") final int subscription,
			@DefaultValue(value = "1") @QueryParam("cpu") final double cpu,
			@DefaultValue(value = "1") @QueryParam("ram") final long ram,
			@QueryParam("constant") final Boolean constant, @QueryParam("type") final String type,
			@QueryParam("location") final String location, @QueryParam("usage") final String usage,
			@QueryParam("license") final String license, @QueryParam("engine") final String engine,
			@QueryParam("edition") final String edition) {
		// Check the security on this subscription
		return lookup(getQuoteFromSubscription(subscription), cpu, ram, constant, type, location, usage, license,
				engine, edition);
	}

	/**
	 * Return a {@link QuoteDatabaseLookup} corresponding to the best price.
	 */
	private QuoteDatabaseLookup lookup(final ProvQuote configuration, final double cpu, final long ram,
			final Boolean constant, final String type, final String location, final String usageName,
			final String license, final String engine, final String edition) {
		final String node = configuration.getSubscription().getNode().getId();
		final int subscription = configuration.getSubscription().getId();
		final double ramR = getRam(configuration, ram);

		// Resolve the location to use
		final int locationR = getLocation(configuration, location, node);

		// Compute the rate to use
		final ProvUsage usage = getUsage(configuration, usageName);
		final double rate = usage.getRate() / 100d;
		final int duration = usage.getDuration();

		// Resolve the required instance type
		final Integer typeId = getType(type, subscription);

		// Resolve the right license model
		final String licenseR = getLicense(configuration, license, engine, this::canByol);
		final String editionR = getEdition(edition);
		final String engineR = getEngine(engine);

		// Return only the first matching instance
		return ipRepository
				.findLowestPrice(node, cpu, ramR, constant, typeId, locationR, rate, duration, licenseR, engineR,
						editionR, PageRequest.of(0, 1))
				.stream().findFirst().map(rs -> newPrice((ProvDatabasePrice) rs[0], (double) rs[2])).orElse(null);
	}

	private String getEngine(final String engine) {
		return StringUtils.trimToNull(StringUtils.upperCase(engine));
	}

	private String getEdition(final String edition) {
		return StringUtils.trimToNull(StringUtils.upperCase(edition));
	}

	private boolean canByol(final String engine) {
		return ENGINE_ORACLE.equalsIgnoreCase(engine);
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
	 * @param engine
	 *            The filtered engine.
	 * @param uriInfo
	 *            filter data.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-license/{engine}")
	public List<String> findLicenses(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		final List<String> result = ipRepository
				.findAllLicenses(subscriptionResource.checkVisible(subscription).getNode().getId(), getEngine(engine));
		result.replaceAll(l -> StringUtils.defaultIfBlank(l, ProvQuoteInstance.LICENSE_INCLUDED));
		return result;
	}

	/**
	 * Return the available database engine for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param uriInfo
	 *            filter data.
	 * @return The available licenses for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/instance-engine")
	public List<String> findEngine(@PathParam("subscription") final int subscription) {
		return ipRepository.findAllEngines(subscriptionResource.checkVisible(subscription).getNode().getId());
	}

	/**
	 * Return the available database edition software for a subscription.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the instances from the associated provider.
	 * @param os
	 *            The filtered OS.
	 * @param uriInfo
	 *            filter data.
	 * @return The available softwares for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/database-edition/{engine}")
	public List<String> findSoftwares(@PathParam("subscription") final int subscription,
			@PathParam("engine") final String engine) {
		return ipRepository.findAllEditions(subscriptionResource.checkVisible(subscription).getNode().getId(),
				getEdition(engine));
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
	 * Build a new {@link QuoteInstanceLookup} from {@link ProvInstancePrice} and computed price.
	 */
	private QuoteDatabaseLookup newPrice(final ProvDatabasePrice ip, final double cost) {
		final QuoteDatabaseLookup result = new QuoteDatabaseLookup();
		result.setCost(round(cost));
		result.setPrice(ip);
		return result;
	}

	@Override
	protected double getCustomCost(final Double cpu, final Integer ram, final ProvDatabasePrice ip) {
		return 0;
	}

}
