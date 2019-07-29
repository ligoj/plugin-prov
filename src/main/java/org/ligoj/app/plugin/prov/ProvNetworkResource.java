/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Arrays;
import java.util.List;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvNetworkRepository;
import org.ligoj.app.plugin.prov.model.ProvNetwork;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Network part of provisioning.
 * 
 * @since 1.8.12
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvNetworkResource extends AbstractLazyResource {

	@Autowired
	private ProvNetworkRepository repository;

	/**
	 * Return the network relationships available for a subscription.
	 *
	 * @param subscription The subscription identifier, will be used to filter the networks from the associated
	 *                     provider. Ownership of the subscription is not performed there, the principal user must have
	 *                     been previously checked.
	 * @return The available network relationships for the given subscription.
	 */
	public List<ProvNetwork> findAll(final int subscription) {
		return repository.findAll(subscription);
	}

	@Override
	public void onDelete(final ResourceType type, final Integer... resources) {
		Arrays.stream(resources).forEach(r -> repository.deleteAllBy("sourceType", type, new String[] { "source" }, r));
		Arrays.stream(resources).forEach(r -> repository.deleteAllBy("targetType", type, new String[] { "target" }, r));
	}

	@Override
	public void onDeleteAll(final ResourceType type, final int quote) {
		repository.deleteAllBy("configuration.id", quote, new String[] { "sourceType" }, type);
		repository.deleteAllBy("configuration.id", quote, new String[] { "targetType" }, type);
	}

	/**
	 * Update the network of a resource. All previous links associated to the resources are deleted and replaced by the
	 * given ones.
	 *
	 * @param subscription The subscription identifier, will be used to filter the networks from the associated
	 *                     provider.
	 * @param type         The related resource type.
	 * @param id           The related resource identifier.
	 * @param io           The new network data related to the resource.
	 */
	@PUT
	@POST
	@Path("{subscription:\\d+}/network/{type}/{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(@PathParam("subscription") final int subscription, @PathParam("type") final ResourceType type,
			@PathParam("id") final int id, final List<NetworkVo> io) {
		// Check the associations and copy attributes to the entity
		var related = resource.findConfigured(getRepository(type), id, subscription);

		onDelete(type, id);
		io.stream().map(t -> {
			// Check the peer exists
			resource.findConfigured(getRepository(t.getPeerType()), t.getPeer(), subscription);

			final ProvNetwork entity = new ProvNetwork();

			// Validate the peer
			if (t.isInbound()) {
				// Incoming case
				entity.setSource(t.getPeer());
				entity.setSourceType(t.getPeerType());
				entity.setTarget(id);
				entity.setTargetType(type);
			} else {
				// Outgoing case
				entity.setSource(id);
				entity.setSourceType(type);
				entity.setTarget(t.getPeer());
				entity.setTargetType(t.getPeerType());
			}
			entity.setName(t.getName());
			entity.setPort(t.getPort());
			entity.setRate(t.getRate());
			entity.setThroughput(t.getThroughput());
			entity.setConfiguration(related.getConfiguration());

			return entity;
		}).forEach(repository::save);
	}

}
