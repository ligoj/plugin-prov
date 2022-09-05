/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base resource class for lazy relationship with {@link ProvResource}.
 */
public abstract class AbstractLazyResource {

	@Autowired
	protected ProvResource resource;

	/**
	 * Must be invoked when a resource is deleted. This is due to the weak relationship between the resources and the
	 * relationships.
	 *
	 * @param type      The deleted resource type.
	 * @param resources The deleted resource identifiers
	 */
	public abstract void onDelete(final ResourceType type, final Integer... resources);

	/**
	 * Must be invoked when all resources of a given type is deleted within a quote. This is due to the weak
	 * relationship between the resources and the relationships.
	 *
	 * @param type  The deleted resource type.
	 * @param quote The quote identifier
	 */
	public abstract void onDeleteAll(final ResourceType type, final int quote);

	/**
	 * Return the repository managing the given resource type.
	 *
	 * @param type The resource type to query.
	 * @return The corresponding {@link BaseProvQuoteRepository} managing the requested type.
	 */
	protected BaseProvQuoteRepository<?> getRepository(final ResourceType type) {
		return resource.getResource(type).getQiRepository();
	}

}
