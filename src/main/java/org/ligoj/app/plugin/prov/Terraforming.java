package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Contract for Terraform compliant provider.
 * 
 * @see https://www.terraform.io/
 */
public interface Terraforming {

	/**
	 * Generate the Terraform configuration in the related output.
	 * 
	 * @param subscription
	 *            The subscription identifier.
	 * @param quote
	 *            The fetched configuration : instance and storage.
	 */
	void terraform(OutputStream output, int subscription, QuoteVo quote) throws IOException;
}
