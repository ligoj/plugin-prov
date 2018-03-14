package org.ligoj.app.plugin.prov.in;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.transaction.Transactional;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.app.resource.node.LongTaskRunnerNode;
import org.ligoj.app.resource.node.NodeResource;
import org.ligoj.bootstrap.core.resource.OnNullReturn404;
import org.ligoj.bootstrap.core.security.SecurityHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Catalog update task runner.
 */
@Slf4j
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ImportCatalogResource implements LongTaskRunnerNode<ImportCatalogStatus, ImportCatalogStatusRepository> {

	@Autowired
	private NodeResource nodeResource;

	@Autowired
	private SecurityHelper securityHelper;

	@Autowired
	@Getter
	private NodeRepository nodeRepository;

	@Autowired
	protected ServicePluginLocator locator;

	@Autowired
	@Getter
	protected ImportCatalogStatusRepository taskRepository;

	@Autowired
	protected ProvInstancePriceRepository ipRepository;

	@Autowired
	protected ProvStorageTypeRepository stRepository;

	@Autowired
	protected ProvInstanceTypeRepository itRepository;

	@Autowired
	protected ProvLocationRepository locationRepository;

	@Autowired
	protected ProvQuoteRepository repository;

	/**
	 * Update the catalog prices of related provider. Asynchronous operation.
	 * 
	 * @param node
	 *            The node (provider) to update.
	 * @return The catalog status.
	 */
	@POST
	@Path("catalog/{node:service:prov:.+}")
	public ImportCatalogStatus updateCatalog(@PathParam("node") final String node) {
		final Node entity = nodeResource.checkWritableNode(node).getTool();
		final ImportCatalogService catalogService = locator.getResource(entity.getId(), ImportCatalogService.class);
		final ImportCatalogStatus task = startTask(entity.getId(), t -> {
			t.setLocation(null);
			t.setNbInstancePrices(null);
			t.setNbInstanceTypes(null);
			t.setNbStorageTypes(null);
			t.setWorkload(0);
			t.setDone(0);
			t.setPhase(null);
		});
		final String user = securityHelper.getLogin();
		// The import execution will done into another thread
		Executors.newSingleThreadExecutor().submit(() -> {
			Thread.sleep(50);
			securityHelper.setUserName(user);
			updateCatalog(catalogService, entity.getId());
			return null;
		});
		return task;
	}

	@Override
	@DELETE
	@Path("catalog/{node:service:prov:.+}")
	@OnNullReturn404
	public ImportCatalogStatus cancel(@PathParam("node") final String node) {
		return LongTaskRunnerNode.super.cancel(nodeResource.checkWritableNode(node).getTool().getId());
	}

	@Override
	@GET
	@Path("catalog/{node:service:prov:[^/]+}")
	public ImportCatalogStatus getTask(@PathParam("node") final String node) {
		// Simple proxy with a different REST path
		return LongTaskRunnerNode.super.getTask(nodeResource.checkWritableNode(node).getTool().getId());
	}

	/**
	 * Update the catalog of given node. Synchronous operation.
	 * 
	 * @param catalogService
	 *            The catalog service related to the provider.
	 * @param node
	 *            The node to update.
	 */
	protected void updateCatalog(final ImportCatalogService catalogService, final String node) {
		// Restore the context
		log.info("Catalog update for {}", node);
		boolean failed = true;
		try {
			catalogService.updateCatalog(node);
			log.info("Catalog update succeed for {}", node);
			failed = false;
		} catch (final Exception e) {
			// Catalog update failed
			log.error("Catalog update failed for {}", node, e);
		} finally {
			endTask(node, failed, t -> {
				if (!t.isFailed()) {
					t.setLastSuccess(t.getEnd());

					// Updated statistics
					updateStats(t);
				}
			});
		}
	}

	/**
	 * Update the statistics of a catalog update task.
	 * 
	 * @param task
	 *            The task status to update.
	 */
	public void updateStats(final ImportCatalogStatus task) {
		updateStats(task, task.getLocked().getId());
	}

	/**
	 * Update the statistics of a catalog update task.
	 * 
	 * @param task
	 *            The task status to update.
	 * @param node
	 *            The node identifier.
	 */
	private void updateStats(final ImportCatalogStatus task, final String node) {
		task.setNbInstancePrices((int) ipRepository.countBy("type.node.id", node));
		task.setNbInstanceTypes((int) itRepository.countBy("node.id", node));
		task.setNbLocations((int) locationRepository.countBy("node.id", node));
		task.setNbStorageTypes((int) stRepository.countBy("node.id", node));
	}

	/**
	 * Return the nodes and their catalog status.
	 * 
	 * @return The nodes and their catalog status.
	 */
	@GET
	@Path("catalog")
	public List<CatalogVo> findAll() {
		// Get all catalogs
		final Map<String, ImportCatalogStatus> statuses = taskRepository.findAllVisible(securityHelper.getLogin()).stream()
				.collect(Collectors.toMap(s -> s.getLocked().getId(), Function.identity()));

		// Complete with nodes without populated catalog
		final Page<Node> providers = nodeRepository.findAllVisible(securityHelper.getLogin(), "", ProvResource.SERVICE_KEY, null, 1,
				PageRequest.of(0, 100));

		return providers.getContent().stream().sorted().map(NodeResource::toVo)
				.map(n -> new CatalogVo(Optional.ofNullable(statuses.get(n.getId())).orElseGet(() -> {
					// Create a mock catalog status
					final ImportCatalogStatus status = new ImportCatalogStatus();
					updateStats(status, n.getId());
					return status;
				}), n, locator.getResource(n.getId(), ImportCatalogService.class) != null, (int) repository.countByNode(n.getId())))
				.collect(Collectors.toList());
	}

	@Override
	public Supplier<ImportCatalogStatus> newTask() {
		return ImportCatalogStatus::new;
	}

}
