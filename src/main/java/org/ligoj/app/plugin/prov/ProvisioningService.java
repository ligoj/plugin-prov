/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Optional;

import org.ligoj.app.plugin.prov.model.VmOs;

/**
 * Provisioning contract.
 */
public interface ProvisioningService {

	/**
	 * Return the OS to lookup from the queried OS.
	 * 
	 * @param os The required OS.
	 * @return The OS used in the database lookup.
	 */
	default VmOs getCatalogOs(VmOs os) {
		return Optional.ofNullable(os).map(VmOs::toPricingOs).orElse(os);
	}
}
