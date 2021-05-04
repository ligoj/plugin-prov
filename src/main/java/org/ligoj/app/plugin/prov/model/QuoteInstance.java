/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote instance query.
 */
public interface QuoteInstance extends QuoteVmOs {

	/**
	 * Return Optional built-in software. May be <code>null</code>. When not <code>null</code> a software constraint is
	 * added. WHen <code>null</code>, installed software is also accepted.
	 *
	 * @return Optional built-in software. May be <code>null</code>. When not <code>null</code> a software constraint is
	 *         added. WHen <code>null</code>, installed software is also accepted.
	 */
	String getSoftware();

	/**
	 * Return the requested tenancy, default is "SHARED".
	 *
	 * @return The requested tenancy, default is "SHARED".
	 */
	ProvTenancy getTenancy();

}
