/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * The tenancy is the way the running instance is isolated from the others.<br>
 * Note the host tenancy is not yet implemented there.
 */
public enum ProvTenancy {

	/**
	 * Shared hardware, related instance host can change.
	 */
	SHARED,

	/**
	 * Dedicated hardware inside a host, but the host still shared.
	 */
	DEDICATED

}
