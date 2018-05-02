package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;

/**
 * Contract for Terraform compliant provider.
 * 
 * @see <a href="https://www.terraform.io/">https://www.terraform.io/</a>
 */
public interface Terraforming {

	/**
	 * Generate the Terraform configuration files.
	 * 
	 * @param context
	 *            The Terraform context holding the subscription, the quote and the user inputs.
	 * @throws IOException
	 *             When Terraform content cannot be written.
	 */
	void generate(Context context) throws IOException;

}
