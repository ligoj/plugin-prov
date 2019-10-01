/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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

import org.ligoj.app.plugin.prov.dao.ProvTagRepository;
import org.ligoj.app.plugin.prov.model.AbstractQuoteResource;
import org.ligoj.app.plugin.prov.model.ProvTag;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.NamedBean;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ProvTagResource extends AbstractLazyResource {

	@Autowired
	private ProvTagRepository repository;

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

	@Override
	public void onDelete(final ResourceType type, final Integer... resources) {
		Arrays.stream(resources).forEach(r -> repository.deleteAllBy("type", type, new String[] { "resource" }, r));
	}

	@Override
	public void onDeleteAll(final ResourceType type, final int quote) {
		repository.deleteAllBy("configuration.id", quote, new String[] { "type" }, type);
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
	 * Update the tag inside a quote.
	 *
	 * @param subscription The subscription identifier, will be used to filter the tags from the associated provider.
	 * @param vo           The new quote tag data.
	 */
	@PUT
	@Path("{subscription:\\d+}/tag")
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(@PathParam("subscription") final int subscription, final TagEditionVo vo) {
		saveOrUpdate(subscription, resource.findConfigured(repository, vo.getId(), subscription), vo);
	}

	/**
	 * Save or update the tag entity from the given {@link ProvTag}. The related subscription, the related resource and
	 * the related resource type must match and be visible for the principal user.
	 * 
	 * @param subscription The subscription identifier, will be used to filter the tags from the associated provider.
	 * @param entity       The target entity to update/persist
	 * @param vo           The new quote tag data.
	 * @return The tag identifier.
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
	 * @param subscription The subscription identifier, will be used to filter the tags from the associated provider.
	 * @param id           The {@link ProvTag} identifier.
	 */
	@DELETE
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("{subscription:\\d+}/tag/{id}")
	public void delete(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		repository.delete(resource.findConfigured(repository, id, subscription));
	}

	/**
	 * Replace the tags of a resource when the given tags are not <code>null</code>. Empty tags collection will remove
	 * all tags of this resource. Only work with persisted resources.
	 * 
	 * @param tags     The optional collection of tags. When <code>null</code>, nothing is done.
	 * @param resource The related resource.
	 */
	public void replaceTags(Collection<TagVo> tags, final AbstractQuoteResource<?> resource) {
		if (tags != null) {
			// Redefine tags for this entity
			onDelete(resource.getResourceType(), resource.getId());
			tags.stream().map(t -> {
				final ProvTag entity = new ProvTag();
				entity.setName(t.getName());
				entity.setValue(t.getValue());
				entity.setResource(resource.getId());
				entity.setType(resource.getResourceType());
				entity.setConfiguration(resource.getConfiguration());
				return entity;
			}).forEach(repository::save);
		}
	}

}
