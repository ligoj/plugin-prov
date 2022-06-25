/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
import org.ligoj.app.plugin.prov.dao.ProvContainerPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvContainerTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
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
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Catalog update task runner.
 */
@Slf4j
@Service
@Path(ProvResource.SERVICE_URL + "/catalog")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ImportCatalogResource implements LongTaskRunnerNode<ImportCatalogStatus, ImportCatalogStatusRepository> {

	private static final String BY_NODE = "node.id";

	@Autowired
	@Getter
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
	private ProvInstancePriceRepository ipRepository;

	@Autowired
	private ProvDatabasePriceRepository dpRepository;

	@Autowired
	private ProvContainerPriceRepository cpRepository;

	@Autowired
	private ProvStorageTypeRepository stRepository;

	@Autowired
	private ProvInstanceTypeRepository itRepository;

	@Autowired
	private ProvContainerTypeRepository ctRepository;

	@Autowired
	private ProvDatabaseTypeRepository dtRepository;

	@Autowired
	private ProvLocationRepository locationRepository;

	@Autowired
	private ProvQuoteRepository repository;

	/**
	 * Update the catalog prices of related provider. Asynchronous operation.
	 *
	 * @param node  The node (provider) to update.
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @return The catalog status.
	 */
	@POST
	@Path("{node:service:prov:.+}")
	public ImportCatalogStatus updateCatalog(@PathParam("node") final String node,
			@QueryParam("force") @DefaultValue("false") final boolean force) {
		final var entity = nodeResource.checkWritableNode(node).getTool();
		final var catalogService = locator.getResource(entity.getId(), ImportCatalogService.class);
		final var task = startTask(entity.getId(), t -> {
			t.setLocation(null);
			t.setNbInstancePrices(null);
			t.setNbInstanceTypes(null);
			t.setNbStorageTypes(null);
			t.setWorkload(0);
			t.setDone(0);
			t.setPhase(null);
		});
		final var user = securityHelper.getLogin();
		// The import execution will done into another thread
		Executors.newSingleThreadExecutor().submit(() -> {
			Thread.sleep(50);
			securityHelper.setUserName(user);
			updateCatalog(catalogService, entity.getId(), force);
			return null;
		});
		return task;
	}

	/**
	 * Update the catalog of given node. Synchronous operation.
	 *
	 * @param catalogService The catalog service related to the provider.
	 * @param node           The node to update.
	 */
	protected void updateCatalog(final ImportCatalogService catalogService, final String node) {
		updateCatalog(catalogService, node, false);
	}

	/**
	 * Update the catalog of given node. Synchronous operation.
	 *
	 * @param catalogService The catalog service related to the provider.
	 * @param force          When <code>true</code>, all cost attributes are update.
	 * @param node           The node to update.
	 */
	protected void updateCatalog(final ImportCatalogService catalogService, final String node, final boolean force) {
		// Restore the context
		log.info("Catalog update for {}", node);
		var failed = true;
		try {
			catalogService.updateCatalog(node, force);
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

	@Override
	@DELETE
	@Path("{node:service:prov:.+}")
	@OnNullReturn404
	public ImportCatalogStatus cancel(@PathParam("node") final String node) {
		return LongTaskRunnerNode.super.cancel(nodeResource.checkWritableNode(node).getTool().getId());
	}

	@Override
	@GET
	@Path("{node:service:prov:[^/]+}")
	public ImportCatalogStatus getTask(@PathParam("node") final String node) {
		// Simple proxy with a different REST path
		return LongTaskRunnerNode.super.getTask(nodeResource.checkWritableNode(node).getTool().getId());
	}

	/**
	 * Update the statistics of a catalog update task.
	 *
	 * @param task The task status to update.
	 */
	public void updateStats(final ImportCatalogStatus task) {
		updateStats(task, task.getLocked().getId());
	}

	/**
	 * Update the statistics of a catalog update task.
	 *
	 * @param task The task status to update.
	 * @param node The node identifier.
	 */
	private void updateStats(final ImportCatalogStatus task, final String node) {
		task.setNbInstancePrices(Stream.of(ipRepository, dpRepository, cpRepository)
				.mapToInt(r -> (int) r.countBy("type.node.id", node)).sum());
		task.setNbInstanceTypes(Stream.of(itRepository, dtRepository, ctRepository)
				.mapToInt(r -> (int) r.countBy(BY_NODE, node)).sum());
		task.setNbLocations((int) locationRepository.countBy(BY_NODE, node));
		task.setNbStorageTypes((int) stRepository.countBy(BY_NODE, node));
	}

	/**
	 * Return the nodes and their catalog status.
	 *
	 * @return The nodes and their catalog status.
	 */
	@GET
	public List<CatalogVo> findAll() {
		// Get all catalogs
		final var statuses = taskRepository.findAllVisible(securityHelper.getLogin()).stream()
				.collect(Collectors.toMap(s -> s.getLocked().getId(), Function.identity()));

		// Complete with nodes without populated catalog
		final var providers = nodeRepository.findAllVisible(securityHelper.getLogin(), "", ProvResource.SERVICE_KEY,
				null, 1, PageRequest.of(0, 100));

		return providers.getContent().stream().sorted().map(n -> {
			final var vo = new CatalogVo();
			vo.setStatus(Optional.ofNullable(statuses.get(n.getId())).orElseGet(() -> {

				// Create a mock catalog status
				final var status = new ImportCatalogStatus();
				updateStats(status, n.getId());
				return status;
			}));
			vo.setNode(NodeResource.toVo(n));
			vo.setCanImport(locator.getResource(n.getId(), ImportCatalogService.class) != null);
			vo.setNbQuotes((int) repository.countByNode(n.getId()));
			vo.setPreferredLocation(
					locationRepository.findBy("node", n, new String[] { "preferred" }, new Object[] { true }));
			return vo;
		}).toList();

	}

	/**
	 * Update catalog .
	 *
	 * @param vo New catalog settings.
	 */
	@PUT
	public void update(CatalogEditionVo vo) {
		final var node = nodeResource.checkWritableNode(vo.getNode()).getTool().getId();
		if (locationRepository.findBy("id", vo.getPreferredLocation()).getNode().getId() != node) {
			throw new ValidationJsonException(node, "node-not-same");
		}
		locationRepository.unsetPreferredLocation(node);
		locationRepository.setPreferredLocation(node, vo.getPreferredLocation());
	}

	@Override
	public Supplier<ImportCatalogStatus> newTask() {
		return ImportCatalogStatus::new;
	}

}
