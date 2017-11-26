package org.ligoj.app.plugin.prov.terraform;

import java.util.function.Supplier;

import javax.transaction.Transactional;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.TerraformStatusRepository;
import org.ligoj.app.plugin.prov.model.TerraformStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.LongTaskRunnerNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;

/**
 * Terraforming task runner resource.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class TerraformRunnerResource implements LongTaskRunnerNode<TerraformStatus, TerraformStatusRepository> {

	@Autowired
	@Getter
	protected TerraformStatusRepository taskRepository;

	@Autowired
	@Getter
	private NodeRepository nodeRepository;

	@Autowired
	protected ServicePluginLocator locator;

	@Override
	public Supplier<TerraformStatus> newTask() {
		return TerraformStatus::new;
	}

}
