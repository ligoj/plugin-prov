/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote instance VM with OS query.
 */
public interface QuoteVmOs extends QuoteVm {

	/**
	 * Return The requested OS, default is "LINUX".
	 *
	 * @return The requested OS, default is "LINUX".
	 */
	VmOs getOs();

}
