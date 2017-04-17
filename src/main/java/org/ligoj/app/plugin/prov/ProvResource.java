package org.ligoj.app.plugin.prov;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.resource.plugin.AbstractConfiguredServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Service;

/**
 * The provisioning service. There is complete quote configuration along the
 * subscription.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
public class ProvResource extends AbstractConfiguredServicePlugin<ProvQuote> {

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = BASE_URL + "/prov";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_KEY = SERVICE_URL.replace('/', ':').substring(1);

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ProvInstancePriceRepository instancePriceRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	protected IamProvider[] iamProvider;

	@Override
	public String getKey() {
		return SERVICE_KEY;
	}

	private Function<String, ? extends UserOrg> toUser() {
		return iamProvider[0].getConfiguration().getUserRepository()::toUser;
	}

	private ProvQuoteStorageVo toStorageVo(final ProvQuoteStorage entity) {
		final ProvQuoteStorageVo vo = new ProvQuoteStorageVo();
		DescribedBean.copy(entity, vo);
		vo.setId(entity.getId());
		vo.setInstance(Optional.ofNullable(entity.getInstance()).map(Persistable::getId).orElse(null));
		vo.setSize(entity.getSize());
		vo.setStorage(entity.getStorage());
		return vo;
	}

	@GET
	@Path("{subscription:\\d+}")
	@Override
	@Transactional
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public QuoteVo getConfiguration(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisibleSubscription(subscription);
		final QuoteVo vo = new QuoteVo();
		final ProvQuote entity = repository.getCompute(subscription);
		DescribedBean.copy(entity, vo);
		vo.copyAuditData(entity, toUser());
		vo.setInstances(entity.getInstances());
		vo.setStorages(
				repository.getStorage(subscription).stream().map(this::toStorageVo).collect(Collectors.toList()));
		return vo;
	}

	/**
	 * Return the quote associated to the given subscription. The visibility is
	 * checked.
	 * 
	 * @param subscription
	 *            The linked subscription.
	 * @return The quote if the visibility has been checked.
	 */
	private ProvQuote getQuoteFromSubscription(final int subscription) {
		return repository.findBy("subscription", subscriptionResource.checkVisibleSubscription(subscription));
	}

	/**
	 * Create the instance inside a quote.
	 * 
	 * @param vo
	 *            The quote instance.
	 * @return The created identifier.
	 */
	@POST
	@PUT
	@Path("{subscription:\\d+}/instance")
	@Consumes(MediaType.APPLICATION_JSON)
	public int saveOrUpdate(@PathParam("subscription") final int subscription, final ProvQuoteInstanceVo vo) {
		final ProvQuoteInstance entity = new ProvQuoteInstance();
		entity.setConfiguration(getQuoteFromSubscription(subscription));
		entity.setInstance(instancePriceRepository.findOneExpected(vo.getInstance()));
		DescribedBean.copy(vo, entity);
		return qiRepository.saveAndFlush(entity).getId();
	}

	/**
	 * Create the storage inside a quote.
	 * 
	 * @param vo
	 *            The quote storage.
	 * @return The created identifier.
	 */
	@POST
	@PUT
	@Path("{subscription:\\d+}/storage")
	@Consumes(MediaType.APPLICATION_JSON)
	public int saveOrUpdate(@PathParam("subscription") final int subscription, final ProvQuoteStorageEditionVo vo) {
		final ProvQuoteStorage entity = new ProvQuoteStorage();
		entity.setConfiguration(getQuoteFromSubscription(subscription));
		entity.setSize(vo.getSize());
		entity.setInstance(Optional.ofNullable(vo.getInstance()).map(qiRepository::findOneExpected).orElse(null));
		DescribedBean.copy(vo, entity);
		return qsRepository.saveAndFlush(entity).getId();
	}

	/**
	 * Return the quote status linked to given subscription.
	 * 
	 * @param subscription
	 *            The parent subscription identifier.
	 * @return The quote status (summary only) linked to given subscription.
	 */
	@Transactional
	@org.springframework.transaction.annotation.Transactional(readOnly = true)
	public QuoteLigthVo getSusbcriptionStatus(final int subscription) {
		final QuoteLigthVo vo = new QuoteLigthVo();
		final Object[] compute = repository.getComputeSummary(subscription).get(0);
		final Object[] storage = repository.getStorageSummary(subscription).get(0);
		final ProvQuote entity = (ProvQuote) compute[0];
		DescribedBean.copy(entity, vo);
		vo.setCost(entity.getCost());
		vo.setNbInstances(((Long) compute[1]).intValue());
		vo.setTotalCpu(((Long) compute[2]).intValue());
		vo.setTotalRam(((Long) compute[3]).intValue());
		vo.setNbStorages(((Long) storage[1]).intValue());
		vo.setTotalStorage(((Long) storage[2]).intValue());
		return vo;
	}

}
