/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Quote instance query.
 */
public interface QuoteInstance extends QuoteVm {

	/**
	 * Return Optional built-in software. May be <code>null</code>. When not <code>null</code> a software constraint is
	 * added. WHen <code>null</code>, installed software is also accepted.
	 *
	 * @return Optional built-in software. May be <code>null</code>. When not <code>null</code> a software constraint is
	 *         added. WHen <code>null</code>, installed software is also accepted.
	 */
	String getSoftware();

	/**
	 * Return The requested OS, default is "LINUX".
	 *
	 * @return The requested OS, default is "LINUX".
	 */
	VmOs getOs();

}
