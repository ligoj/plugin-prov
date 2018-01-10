package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;
import java.io.OutputStream;

import org.ligoj.app.plugin.prov.QuoteVo;

/**
 * Contract for Terraform compliant provider.
 * 
 * @see <a href="https://www.terraform.io/">https://www.terraform.io/</a>
 */
public interface Terraforming {

	/**
	 * Generate the Terraform configuration in the related output.
	 * 
	 * @param output
	 *            The target output for
	 * @param subscription
	 *            The subscription identifier.
	 * @param quote
	 *            The fetched configuration : instance and storage.
	 * @throws IOException
	 *             When Terraform content cannot be written.
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
