package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;
import java.io.OutputStream;

import org.ligoj.app.plugin.prov.QuoteVo;

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

	/**
	 * Return parameters used during Terraform command execution.
	 * 
	 * @param subscription
	 *            The subscription identifier.
	 * @return parameters array
	 */
	String[] commandLineParameters(int subscription);
}
