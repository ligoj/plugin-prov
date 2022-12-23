/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

/**
 * Terraform sequence name. Is a fancy name to group a list of Terraform commands to execute.
 *
 */
public enum TerraformSequence {

	/**
	 * Will do every thing to configure the target resources including the initialization and the progress management.
	 */
	CREATE,

	/**
	 * Will do every thing to destroy the target resources including the progress management.
	 */
	DESTROY
}
