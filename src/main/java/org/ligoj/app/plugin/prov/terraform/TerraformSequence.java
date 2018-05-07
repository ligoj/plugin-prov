package org.ligoj.app.plugin.prov.terraform;

/**
 * Terraform sequence name. Is a fancy name to group a list of Terraform commands to execute.
 *
 */
public enum TerraformSequence {

	/**
	 * Will do every thing to setup the target resources including the initialization and the progress management.
	 */
	CREATE,

	/**
	 * Will do every thing to destroy the target resources including the progress management.
	 */
	DESTROY;
}
