package org.ligoj.app.plugin.prov;

import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.api.ConfigurablePlugin;
import org.ligoj.app.iam.IamProvider;
import org.ligoj.app.iam.UserOrg;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.plugin.AbstractServicePlugin;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.DescribedBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Persistable;
import org.springframework.stereotype.Service;

/**
 * The Virtual Machine service.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
public class ProvResource extends AbstractServicePlugin implements ConfigurablePlugin {

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
	protected ServicePluginLocator servicePluginLocator;

	@Autowired
	private ProvQuoteRepository repository;

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
