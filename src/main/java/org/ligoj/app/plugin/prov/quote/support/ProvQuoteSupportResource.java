/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.support;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.AbstractProvQuoteResource;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.UpdatedCost;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSupportRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportTypeRepository;
import org.ligoj.app.plugin.prov.model.Costed;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.core.DescribedBean;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The support plan part of the provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteSupportResource
		extends AbstractProvQuoteResource<ProvSupportType, ProvSupportPrice, ProvQuoteSupport, QuoteSupportEditionVo> {

	@Autowired
	private ProvSupportTypeRepository stRepository;

	@Autowired
	private ProvSupportPriceRepository spRepository;

	@Autowired
	private ProvQuoteSupportRepository qsRepository;

	@DELETE
	@Path("{subscription:\\d+}/support")
	@Consumes(MediaType.APPLICATION_JSON)
	@Override
	public UpdatedCost deleteAll(@PathParam("subscription") final int subscription) {
		return super.deleteAll(subscription);
	}

	@Override
	@POST
	@Path("support")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost create(final QuoteSupportEditionVo vo) {
		return saveOrUpdate(new ProvQuoteSupport(), vo);
	}

	@Override
	@PUT
	@Path("support")
	@Consumes(MediaType.APPLICATION_JSON)
	public UpdatedCost update(final QuoteSupportEditionVo vo) {
		return saveOrUpdate(resource.findConfigured(qsRepository, vo.getId()), vo);
	}

	@Override
	public FloatingCost refresh(final ProvQuoteSupport qs) {
		final var quote = qs.getConfiguration();

		// Find the lowest price
		qs.setPrice(validateLookup("support-plan", lookup(quote, qs.getSeats(), qs.getAccessApi(), qs.getAccessEmail(),
				qs.getAccessChat(), qs.getAccessPhone(), qs.getLevel()).stream().findFirst().orElse(null),
				qs.getName()));
		return updateCost(qs).round();
	}

	/**
	 * Check and return the support price matching to the requirements and related name.
	 */
	private ProvSupportPrice findByTypeName(final int subscription, final String name) {
		return assertFound(spRepository.findByTypeName(subscription, name), name);
	}

	/**
	 * Save or update the support inside a quote.
	 *
	 * @param entity The support entity to update.
	 * @param vo     The new quote support data to persist.
	 * @return The formal entity.
	 */
	private UpdatedCost saveOrUpdate(final ProvQuoteSupport entity, final QuoteSupportEditionVo vo) {
		DescribedBean.copy(vo, entity);

		// Check the associations
		final int subscription = vo.getSubscription();
		final var quote = getQuoteFromSubscription(subscription);
		entity.setName(vo.getName());
		entity.setDescription(vo.getDescription());
		entity.setConfiguration(quote);
		entity.setPrice(findByTypeName(subscription, vo.getType()));
		entity.setSeats(vo.getSeats());
		entity.setAccessApi(vo.getAccessApi());
		entity.setAccessEmail(vo.getAccessEmail());
		entity.setAccessChat(vo.getAccessChat());
		entity.setAccessPhone(vo.getAccessPhone());
		entity.setLevel(vo.getLevel());

		// Check the support requirements to validate the linked price
		final var type = entity.getPrice().getType();
		if (lookup(quote, vo.getSeats(), vo.getAccessApi(), vo.getAccessEmail(), vo.getAccessChat(),
				vo.getAccessPhone(), vo.getLevel()).stream().map(qs -> qs.getPrice().getType())
						.noneMatch(type::equals)) {
			// The related support type does not match these requirements
			throw new ValidationJsonException("type", "type-incompatible-requirements", type.getName());
		}

		// Save and update the costs
		final var update = newUpdateCost(entity);

		// Add tags
		super.saveOrUpdate(entity, vo);
		return update;
	}

	/**
	 * Request a cost update of the given entity and report the delta to the the global cost. The changes are persisted.
	 *
	 * @param entity The quote support to update.
	 * @return The new computed cost.
	 */
	protected UpdatedCost newUpdateCost(final ProvQuoteSupport entity) {
		return newUpdateCost(qsRepository, entity, this::updateCost);
	}

	@Override
	public <T extends Costed> void addCost(final T entity, final double old, final double oldMax,
			final double oldInitial, final double oldMaxInitial) {
		// Report the delta to the quote. Initial costs are not updated
		final var quote = entity.getConfiguration();
		quote.setCost(round(quote.getCost() + entity.getCost() - old));
		quote.setMaxCost(round(quote.getMaxCost() + entity.getMaxCost() - oldMax));
		quote.setCostSupport(round(quote.getCostSupport() + entity.getCost() - old));
		quote.setMaxCostSupport(round(quote.getMaxCostSupport() + entity.getMaxCost() - oldMax));
	}

	@DELETE
	@Path("support/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Override
	public UpdatedCost delete(@PathParam("id") final int id) {
		return super.delete(id);
	}

	/**
	 * Return the support types the support inside a quote.
	 *
	 * @param subscription The subscription identifier, will be used to filter the supports from the associated
	 *                     provider.
	 * @param uriInfo      filter data.
	 * @return The valid support types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/support-type")
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<ProvSupportType> findType(@PathParam("subscription") final int subscription,
			@Context final UriInfo uriInfo) {
		subscriptionResource.checkVisible(subscription);
		return paginationJson.applyPagination(uriInfo,
				stRepository.findAll(subscription, DataTableAttributes.getSearch(uriInfo),
						paginationJson.getPageRequest(uriInfo, ProvResource.ORM_COLUMNS)),
				Function.identity());
	}

	/**
	 * Return the available support types from the provider linked to the given subscription..
	 *
	 * @param subscription The subscription identifier, will be used to filter the support types from the associated
	 *                     provider.
	 * @param seats        Who can open cases. When <code>null</code>, unlimited requirement.
	 * @param accessApi    API access. <code>null</code> when is not required.
	 * @param accessEmail  Mail access. <code>null</code> when is not required.
	 * @param accessChat   Chat access. <code>null</code> when is not required.
	 * @param accessPhone  Phone access. <code>null</code> when is not required.
	 * @param level        Optional consulting services level: WORST=reserved, LOW=generalGuidance,
	 *                     MEDIUM=contextualGuidance, GOOD=contextualReview, BEST=reserved.
	 * @return The valid support types for the given subscription.
	 */
	@GET
	@Path("{subscription:\\d+}/support-lookup")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<QuoteSupportLookup> lookup(@PathParam("subscription") final int subscription,
			@QueryParam("seats") final Integer seats, @QueryParam("access-api") final SupportType accessApi,
			@QueryParam("access-email") final SupportType accessEmail,
			@QueryParam("access-chat") final SupportType accessChat,
			@QueryParam("access-phone") final SupportType accessPhone, @QueryParam("level") final Rate level) {

		// Check the security on this subscription
		return lookup(getQuoteFromSubscription(subscription), seats, accessApi, accessEmail, accessChat, accessPhone,
				level);
	}

	private List<QuoteSupportLookup> lookup(final ProvQuote quote, final Integer seats, final SupportType accessApi,
			final SupportType accessEmail, final SupportType accessChat, final SupportType accessPhone,
			final Rate level) {

		// Get the attached node and check the security on this subscription
		final var node = quote.getSubscription().getNode().getRefined().getId();
		return spRepository.findAll(node).stream().filter(sp -> sp.getType().getSeats() == null || seats != null)
				.filter(sp -> filter(accessApi, sp.getType().getAccessApi()))
				.filter(sp -> filter(accessChat, sp.getType().getAccessChat()))
				.filter(sp -> filter(accessEmail, sp.getType().getAccessEmail()))
				.filter(sp -> filter(accessPhone, sp.getType().getAccessPhone()))
				.filter(sp -> filter(level, sp.getType().getLevel())).map(sp -> newPrice(quote, sp, seats))
				.sorted((p1, p2) -> (int) (p1.getCost() - p2.getCost())).collect(Collectors.toList());
	}

	/**
	 * Accept when required support is provided.
	 *
	 * @param quote    The requirement.
	 * @param provided The provided rate.
	 * @return <code>true</code> when required support is provided.
	 */
	public boolean filter(final SupportType quote, final SupportType provided) {
		return quote == null || provided == SupportType.ALL || quote == provided;
	}

	/**
	 * Accept when required rate is provided.
	 *
	 * @param quote    The requirement.
	 * @param provided The provided rate.
	 * @return <code>true</code> when required rate is provided.
	 */
	public boolean filter(final Rate quote, final Rate provided) {
		return quote == null || (provided != null && quote.ordinal() <= provided.ordinal());
	}

	/**
	 * Build a new {@link QuoteSupportLookup} from {@link ProvSupportPrice} and computed price.
	 */
	private QuoteSupportLookup newPrice(final ProvQuote quote, final ProvSupportPrice price, final Integer seats) {
		final var result = new QuoteSupportLookup();
		final var rates = toIntArray(price.getRate());
		final var limits = toIntArray(price.getLimit());
		result.setCost(round(getCost(seats, quote.getCostNoSupport(), price, rates, limits)));
		result.setPrice(price);
		result.setSeats(seats);
		return result;
	}

	@Override
	public FloatingCost getCost(final ProvQuoteSupport entity) {
		final var quote = entity.getConfiguration();
		final var price = entity.getPrice();
		final var rates = toIntArray(price.getRate());
		final var limits = toIntArray(price.getLimit());
		final var seats = entity.getSeats();
		return new FloatingCost(getCost(seats, quote.getCostNoSupport(), price, rates, limits),
				getCost(seats, quote.getMaxCostNoSupport(), price, rates, limits), quote.getInitialCost(),
				quote.getMaxInitialCost(), quote.isUnboundCost()).round();
	}

	private Double getCost(final Integer seats, final double cost, final ProvSupportPrice price, final int[] rates,
			final int[] limits) {
		// Compute the group of required seats
		final var nb = Math.max(1, Optional.ofNullable(price.getType().getSeats())
				.map(s -> (int) Math.ceil((double) seats / s)).orElse(1));
		// Compute the cost of the seats and the rates
		return nb * (computeRates(cost, price.getMin(), rates, limits) + price.getCost());
	}

	private int[] toIntArray(String rawString) {
		return Arrays.stream(StringUtils.split(ObjectUtils.defaultIfNull(rawString, ""), ","))
				.mapToInt(Integer::parseInt).toArray();
	}

	/**
	 * Apply successive rates following this computation:<br>
	 * <p>
	 * <code>
	   Math.max(plan.getMin();<br>
	   Math.max(0;Math.min(cost;limit3)-limit2)*rate3 +<br>
	   Math.max(0;Math.min(cost;limit2)-limit1)*rate2 +<br>
	   Math.max(0;Math.min(cost;limit1)-limit0)*rate1 +<br>
	   Math.max(0;Math.min(cost;limit0)-0)*rate0)</code>
	 * </p>
	 *
	 * @param cost   The total cost without support cost.
	 * @param min    The minimal cost, whatever the computation result.
	 * @param rates  The base 100 percentage to apply to a segment of cost. The segment is
	 *               <code>limit[index-1, index]</code> where index is the current index of the rate within the array.
	 *               When <code>index=0</code>, <code>limit[-1]=0</code>. When <code>index&gt;limit.length-1</code>,
	 *               <code>limit=Integer.MAX_VALUE</code>.
	 * @param limits The segment upper limit where the corresponding rate can be applied. The length of this array is
	 *               lesser or equals than the <code>rates</code> array.
	 * @return The added computed support cost of each segment.
	 */
	public double computeRates(final double cost, final int min, final int[] rates, final int[] limits) {
		double support = 0;
		for (var i = rates.length; i-- > 0;) {
			support += Math.max(0, Math.min(cost, i > limits.length - 1 ? Integer.MAX_VALUE : limits[i])
					- (i == 0 ? 0 : limits[i - 1])) / 100 * rates[i];
		}
		return Math.max(min, support);
	}

	@Override
	protected ResourceType getType() {
		return ResourceType.SUPPORT;
	}

	@Override
	protected BaseProvQuoteRepository<ProvQuoteSupport> getResourceRepository() {
		return qsRepository;
	}

}
