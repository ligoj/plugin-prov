/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Map;

import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The base class for provisioning tool. There is complete quote configuration along the subscription.
 */
public abstract class AbstractProvResource extends AbstractToolPluginResource implements ProvisioningService {

	@Autowired
	protected ProvResource provResource;

	@Autowired
	protected NodeResource nodeResource;

	@Override
	public SubscriptionStatusWithData checkSubscriptionStatus(final int subscription, final String node,
			final Map<String, String> parameters) {
		final var status = new SubscriptionStatusWithData();

		// Complete the tool status with the generic quote data
		status.put("quote", provResource.getSubscriptionStatus(subscription));
		return status;
	}
}
