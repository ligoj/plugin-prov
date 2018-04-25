package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;

import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.QuoteVo;

/**
 * Contract for Terraform compliant provider.
 * 
 * @see <a href="https://www.terraform.io/">https://www.terraform.io/</a>
 */
public interface Terraforming {

	/**
	 * Generate the Terraform configuration files. There is no constraint of file naming but the <code>.tf</code>
	 * extension name.
	 * 
	 * @param subscription
	 *            The subscription.
	 * @param quote
	 *            The fetched configuration : instance and storage.
	 * @throws IOException
	 *             When Terraform content cannot be written.
	 */
	void generate(Subscription subscription, QuoteVo quote) throws IOException;

}
