/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteResourceRepository;
import org.ligoj.app.plugin.prov.dao.ProvTagRepository;
import org.ligoj.app.plugin.prov.model.ProvTag;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.NamedBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Tag part of provisioning.
 * 
 * @since 1.8.5
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvTagResource {

	@Autowired
	private ProvTagRepository repository;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ApplicationContext context;

	/**
	 * Return the tags available for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the tags from the associated provider.
	 *                     Ownership of the subscription is not performed there, the principal user must have been
	 *                     previously checked.
	 * @return The available tags for the given subscription.
	 */
	public Map<ResourceType, Map<Integer, List<TagVo>>> findAll(final int subscription) {
		final Map<ResourceType, Map<Integer, List<TagVo>>> tags = new EnumMap<>(ResourceType.class);
		repository.findAll(subscription).forEach(t -> tags.computeIfAbsent(t.getType(), a -> new HashMap<>())
				.computeIfAbsent(t.getResource(), a -> new ArrayList<>()).add(toVo(t)));
		return tags;
	}

	private TagVo toVo(final ProvTag entity) {
		var result = new TagVo();
		NamedBean.copy(entity, result);
		result.setValue(entity.getValue());
		return result;
	}

	/**
	 * Must be invoked when a resource is deleted. This is due to the weak relationship between the resource and the
	 * related tags.
	 * 
	 * @param type     The deleted resource type.
	 * @param resource The deleted resource identifiers
	 * @return The available tags for the given subscription.
	 */
	public void onDelete(final ResourceType type, final Integer... resources) {
		Arrays.stream(resources).forEach(r -> repository.deleteAllBy("type", type, new String[] { "resource" }, r));
	}

	/**
	 * Must be invoked when all resources of a given type is deleted within a quote. This is due to the weak
	 * relationship between the resource and the related tags.
	 * 
	 * @param type  The deleted resource type.
	 * @param quote The quote identifier
	 * @return The available tags for the given subscription.
	 */
	public void onDeleteAll(final ResourceType type, final int quote) {
		repository.deleteAllBy("configuration.id", quote, new String[] { "type" }, type);
	}

	/**
	 * Return the repository managing the given resource type.
	 */
	private BaseProvQuoteResourceRepository<?> getRepository(final ResourceType type) {
		return context.getBean("provQuote" + StringUtils.capitalize(type.name().toLowerCase()) + "Repository",
				BaseProvQuoteResourceRepository.class);
	}

	/**
	 * Create the tags inside a quote.
	 *
	 * @param subscription The subscription identifier, will be used to filter the tags from the associated provider.
	 * @param vo           The quote tag.
	 * @return The created tag identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/tag")
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(@PathParam("subscription") final int subscription, final TagEditionVo vo) {
		return saveOrUpdate(subscription, new ProvTag(), vo);
	}

	/**
	 * Update the usage inside a quote. The computed cost are recursively updated from the related instances to the
	 * quote total cost.<br>
	 * The cost of all instances related to this usage will be updated to get the new price.<br>
	 * An instance related to this usage is either an instance explicitly linked to this usage, either an instance
	 * linked to a quote having this usage as default.
	 *
	 * @param subscription The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param name         The quote usage's name to update.
	 * @param vo           The new quote usage data.
	 * @return The updated cost. Only relevant when at least one resource was associated to this usage.
	 */
	@PUT
	@Path("{subscription:\\d+}/tag")
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(@PathParam("subscription") final int subscription, final TagEditionVo vo) {
		saveOrUpdate(subscription, resource.findConfigured(repository, vo.getId(), subscription), vo);
	}

	/**
	 * Save or update the tag entity from the given {@link TagEditionVo}. The related subscription, the related resource
	 * and the related resource type must match and be visible for the principal user.
	 */
	private int saveOrUpdate(final int subscription, final ProvTag entity, final TagEditionVo vo) {
		// Check the associations and copy attributes to the entity
		var res = resource.findConfigured(getRepository(vo.getType()), vo.getResource(), subscription);
		NamedBean.copy(vo, entity);
		entity.setValue(vo.getValue());
		entity.setResource(vo.getResource());
		entity.setType(vo.getType());
		entity.setConfiguration(res.getConfiguration());
		return repository.saveAndFlush(entity).getId();
	}

	/**
	 * Delete a tag.
	 *
	 * @param subscription The subscription identifier, will be used to filter the usages from the associated provider.
	 * @param id           The {@link ProvTag} identifier.
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("{subscription:\\d+}/tag/{id}")
	public void delete(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		repository.delete(resource.findConfigured(repository, id, subscription));
	}

}
