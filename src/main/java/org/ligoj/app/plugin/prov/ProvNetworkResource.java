/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvNetworkRepository;
import org.ligoj.app.plugin.prov.model.ProvNetwork;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
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

	private void validateId(final ResourceType type, final Integer id, final Map<ResourceType, Set<Integer>> existing) {
		if (!existing.get(type).contains(id)) {
			throw new EntityNotFoundException(id.toString());
		}
	}

	/**
	 * Update the network of all resources. All previous links associated to the subscription are deleted and replaced
	 * by the given ones.
	 *
	 * @param subscription The subscription identifier, will be used to filter the networks from the associated
	 *                     provider.
	 * @param io           The new network links related to the subscription.
	 */
	@PUT
	@Path("{subscription:\\d+}/network")
	@Consumes(MediaType.APPLICATION_JSON)
	public void updateAllById(@PathParam("subscription") final int subscription, final List<NetworkFullVo> io) {
		final var quote = deleteAll(subscription);

		// Get all resources identifiers grouped by types
		final var ids = new EnumMap<ResourceType, Set<Integer>>(ResourceType.class);
		Arrays.stream(ResourceType.values()).filter(ResourceType::isNetwork)
				.forEach(t -> ids.put(t, getRepository(t).findAllNetworkId(subscription)));

		io.stream().map(t -> {
			// Check the peer is in this subscription
			validateId(t.getPeerType(), t.getPeer(), ids);
			validateId(t.getSourceType(), t.getSource(), ids);
			return newNetwork(t.getSourceType(), t.getSource(), quote, t);
		}).forEach(repository::save);
	}

	/**
	 * Update the network of all resources using their name instead of their identifier. All previous links associated
	 * to the subscription are deleted and replaced by the given ones.
	 *
	 * @param subscription    The subscription identifier, will be used to filter the networks from the associated
	 *                        provider.
	 * @param io              The new network links related to the subscription.
	 * @param continueOnError When <code>true</code> the resolution error are ignored.
	 * @return Error count. Only relevant when <code>continueOnError</code> is <code>true</code>.
	 * @see #updateAllById(int, List)
	 */
	@PUT
	@Path("{subscription:\\d+}/network-name")
	@Consumes(MediaType.APPLICATION_JSON)
	public int updateAllByName(@PathParam("subscription") final int subscription,
			@QueryParam("continue-on-error") @DefaultValue("false") final boolean continueOnError,
			final List<NetworkFullByNameVo> io) {
		final var quote = deleteAll(subscription);
		var errors = new AtomicInteger();

		// Get all resources identifiers grouped by types
		final var idAndNames = new EnumMap<ResourceType, Map<Integer, String>>(ResourceType.class);
		Arrays.stream(ResourceType.values()).filter(ResourceType::isNetwork)
				.forEach(t -> idAndNames.put(t, getRepository(t).findAllNetworkIdName(subscription).stream()
						.collect(Collectors.toMap(o -> (Integer) o[0], o -> (String) o[1]))));

		// Build a reversed map with duplicated name handling
		final var nameAndIds = new EnumMap<ResourceType, Map<String, Integer>>(ResourceType.class);
		final var counters = new HashMap<String, Integer>();
		idAndNames.keySet().forEach(t -> {
			final var names = new HashMap<String, Integer>();
			nameAndIds.put(t, names);
			idAndNames.get(t).forEach((id, name) -> {
				counters.computeIfPresent(name, (n, o) -> 2);
				counters.computeIfAbsent(name, n -> 1);
				names.computeIfAbsent(name, n -> id);
			});
		});
		io.forEach(n -> {
			// Check the peer is in this subscription
			final var entity = new ProvNetwork();
			if (validateName(nameAndIds, counters, n.getSource(), entity::setSource, entity::setSourceType,
					continueOnError)
					&& validateName(nameAndIds, counters, n.getPeer(), entity::setTarget, entity::setTargetType,
							continueOnError)) {
				repository.save(copyNetworkData(quote, n, entity));
			} else {
				errors.incrementAndGet();
			}
		});
		repository.flush();
		return errors.get();
	}

	/**
	 * Validate the given name can be resolved to an unique resource identifier.
	 */
	private boolean validateName(final Map<ResourceType, Map<String, Integer>> nameAndIds,
			final Map<String, Integer> counters, final String name, final IntConsumer setId,
			final Consumer<ResourceType> setType, final boolean continueOnError) {
		if (!counters.containsKey(name)) {
			// No resource with this name has been found
			if (continueOnError) {
				return false;
			}
			throw new EntityNotFoundException(name);
		}
		// At least one resource with this name has been found
		ValidationJsonException.assertTrue(counters.get(name).intValue() == 1, "ambiguous-name", "name", name);

		nameAndIds.entrySet().stream().filter(e -> e.getValue().containsKey(name)).limit(1).forEach(e -> {
			setId.accept(e.getValue().get(name));
			setType.accept(e.getKey());
		});
		return true;
	}

	private ProvQuote deleteAll(final int subscription) {
		// First delete all IO of this subscription
		final var quote = resource.getQuoteFromSubscription(subscription);
		repository.deleteAll(quote.getId());
		return quote;
	}

	/**
	 * Update the network of a resource. All previous links associated to the resources are deleted and replaced by the
	 * given ones.
	 *
	 * @param subscription The subscription identifier, will be used to filter the networks from the associated
	 *                     provider.
	 * @param type         The related resource type.
	 * @param id           The related resource identifier.
	 * @param io           The new network links related to the resource.
	 */
	@PUT
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
			return newNetwork(type, id, related.getConfiguration(), t);
		}).forEach(repository::save);
	}

	/**
	 * Create an new {@link ProvNetwork} instance from the user data. Identifiers and consistency are not checked.
	 */
	private ProvNetwork newNetwork(final ResourceType type, final Integer id, final ProvQuote quote,
			final NetworkVo t) {
		final var entity = new ProvNetwork();

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
		copyNetworkData(quote, t, entity);
		return entity;
	}

	/**
	 * Copy the common network data to the given entity
	 */
	private <N extends AbstractNetworkVo> ProvNetwork copyNetworkData(final ProvQuote quote, final N vo,
			final ProvNetwork entity) {
		entity.setName(vo.getName());
		entity.setPort(vo.getPort());
		entity.setRate(vo.getRate());
		entity.setThroughput(vo.getThroughput());
		entity.setConfiguration(quote);
		return entity;
	}

}
