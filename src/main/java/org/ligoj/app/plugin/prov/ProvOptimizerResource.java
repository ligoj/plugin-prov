/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.transaction.Transactional;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvOptimizerRepository;
import org.ligoj.app.plugin.prov.model.ProvOptimizer;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ResourceScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * Usage part of provisioning.
 */
@Service
@Path(ProvResource.SERVICE_URL + "/{subscription:\\d+}/optimizer")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvOptimizerResource extends AbstractMultiScopedResource<ProvOptimizer, ProvOptimizerRepository, OptimizerEditionVo> {

	@Autowired
	@Getter
	private ProvOptimizerRepository repository;

	/**
	 * Create a usage initiated without any cost.
	 */
	public ProvOptimizerResource() {
		super(ResourceScope::getOptimizer, ResourceScope::setOptimizer, ProvOptimizer::new, ProvQuote::getOptimizers);
	}

	@Override
	protected UpdatedCost saveOrUpdate(final ProvOptimizer entity, final OptimizerEditionVo vo) {
		// Check the associations and copy attributes to the entity
		entity.setMode(vo.getMode());
		return super.saveOrUpdateInternal(entity, vo);

	}

}
