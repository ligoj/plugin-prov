package org.ligoj.app.plugin.prov;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.model.ProvInstance;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceType;
import org.ligoj.app.plugin.prov.model.ProvStorage;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base class for provisioning tool. There is complete quote configuration
 * along the subscription.
 */
public abstract class AbstractProvResource extends AbstractToolPluginResource {

	@Autowired
	protected ProvResource provResource;

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final int subscription, final String node,
			final Map<String, String> parameters) throws Exception {
		final SubscriptionStatusWithData status = super.checkSubscriptionStatus(subscription, node,
				parameters);

		// Complete the tool status with the generic quote data
		status.put("quote", provResource.getSusbcriptionStatus(subscription));
		return status;
	}

	@Override
	public List<Class<?>> getInstalledEntities() {
		return Arrays.asList(Node.class, ProvInstancePriceType.class, ProvInstance.class, ProvInstancePrice.class,
				ProvStorage.class);
	}

	@Override
	public void link(final int subscription) throws Exception {
		throw new NotImplementedException("");
	}

	@Override
	public void create(final int subscription) throws Exception {
		// Nothing to do
	}
}
