/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceScope;
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
@Getter
public class ProvUsageResource extends AbstractMultiScopedResource<ProvUsage, ProvUsageRepository, UsageEditionVo> {

	@Autowired
	private ProvUsageRepository repository;

	/**
	 * Create a usage initiated without any cost.
	 */
	public ProvUsageResource() {
		super(ResourceScope::getUsage, ResourceScope::setUsage, ProvUsage::new, ProvQuote::getUsages);
	}

	@Override
	protected UpdatedCost saveOrUpdate(final ProvUsage entity, final UsageEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setRate(vo.getRate()).setDuration(vo.getDuration()).setStart(vo.getStart());
		entity.setConvertibleEngine(vo.getConvertibleEngine()).setConvertibleOs(vo.getConvertibleOs())
				.setConvertibleType(vo.getConvertibleType()).setConvertibleFamily(vo.getConvertibleFamily())
				.setConvertibleLocation(vo.getConvertibleLocation()).setReservation(vo.getReservation());
		return super.saveOrUpdateInternal(entity, vo);
	}

}
