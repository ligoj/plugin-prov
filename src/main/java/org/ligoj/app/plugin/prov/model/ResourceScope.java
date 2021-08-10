/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

/**
 * Properties holder of a resource at a specific scope.
 */
public interface ResourceScope {

	/**
	 * Optional usage for this resource when different from the related quote.
	 *
	 * @return usage for this resource when different from the related quote. May be <code>null</code>
	 */
	ProvUsage getUsage();

	/**
	 * Usage for this resource when different from the related quote.
	 *
	 * @param usage The new usage for this resource when different from the related quote. May be <code>null</code>
	 */
	void setUsage(ProvUsage usage);

	/**
	 * Optional budget for this resource when different from the related quote. May be <code>null</code>
	 *
	 * @return budget for this resource when different from the related quote. May be <code>null</code>
	 */
	ProvBudget getBudget();

	/**
	 * Optional budget for this resource when different from the related quote.
	 *
	 * @param budget The new budget for this resource when different from the related quote. May be <code>null</code>
	 */
	void setBudget(ProvBudget budget);

}
