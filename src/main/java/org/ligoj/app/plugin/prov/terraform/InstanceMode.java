package org.ligoj.app.plugin.prov.terraform;

/**
 * AWS Instance type depending on the requirements
 */
public enum InstanceMode {
	/**
	 * Single VM
	 */
	VM,

	/**
	 * Auto Scaling
	 */
	AUTO_SCALING,

	/**
	 * Ephemeral VM
	 */
	EPHEMERAL
}
