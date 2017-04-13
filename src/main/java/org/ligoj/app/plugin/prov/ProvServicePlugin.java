package org.ligoj.app.plugin.prov;

import java.util.Map;

import org.ligoj.app.api.SubscriptionStatusWithData;
import org.ligoj.app.api.ToolPlugin;
import org.ligoj.bootstrap.core.SpringUtils;

/**
 * Features of provisioning implementations.
 */
public interface ProvServicePlugin extends ToolPlugin {

	@Override
	default SubscriptionStatusWithData checkSubscriptionStatus(int subscription, String node, Map<String, String> parameters) throws Exception {
		final SubscriptionStatusWithData status = ToolPlugin.super.checkSubscriptionStatus(subscription, node, parameters);
		
		// Complete the tool status with the generic quote data
		status.put("quote", SpringUtils.getBean(ProvResource.class).getSusbcriptionStatus(subscription));
		return status;
	}
}
