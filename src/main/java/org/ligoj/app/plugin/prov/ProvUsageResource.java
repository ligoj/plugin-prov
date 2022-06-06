/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import javax.transaction.Transactional;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.hibernate.Hibernate;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceScope;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * Usage part of provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL + "/{subscription:\\d+}/usage")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvUsageResource extends AbstractMultiScopedResource<ProvUsage, ProvUsageRepository, UsageEditionVo> {

	@Autowired
	@Getter
	private ProvUsageRepository repository;

	/**
	 * Create a usage initiated without any cost.
	 */
	public ProvUsageResource() {
		super(ResourceScope::getUsage, ResourceScope::setUsage, ProvUsage::new);
	}

	@Override
	protected UpdatedCost saveOrUpdate(final ProvUsage entity, final UsageEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setName(vo.getName());
		entity.setRate(vo.getRate()).setDuration(vo.getDuration()).setStart(vo.getStart());
		entity.setConvertibleEngine(vo.getConvertibleEngine()).setConvertibleOs(vo.getConvertibleOs())
				.setConvertibleType(vo.getConvertibleType()).setConvertibleFamily(vo.getConvertibleFamily())
				.setConvertibleLocation(vo.getConvertibleLocation()).setReservation(vo.getReservation());

		// Fetch the usages of this quotes
		final var quote = entity.getConfiguration();
		Hibernate.initialize(quote.getUsages());

		// Prepare the updated cost of updated instances
		final var relatedCosts = Collections
				.synchronizedMap(new EnumMap<ResourceType, Map<Integer, Floating>>(ResourceType.class));
		// Prevent useless computation, check the relations
		if (entity.getId() != null) {
			// This is an update, update the cost of all related instances
			final var instances = getRelated(getRepository()::findRelatedInstances, entity);
			final var databases = getRelated(getRepository()::findRelatedDatabases, entity);
			final var containers = getRelated(getRepository()::findRelatedContainers, entity);
			final var functions = getRelated(getRepository()::findRelatedFunctions, entity);
			bRessource.lean(quote, instances, databases, containers, functions, relatedCosts);
		}

		repository.saveAndFlush(entity);

		// Update accordingly the support costs
		final var cost = new UpdatedCost(entity.getId());
		cost.setRelated(relatedCosts);
		return resource.refreshSupportCost(cost, quote);
	}

}
