/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test class of {@link ImportCatalogResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class ImportCatalogResourceTest extends AbstractAppTest {

	@Autowired
	private ImportCatalogStatusRepository repository;
	@Autowired
	private NodeRepository nodeRepository;
	@Autowired
	private ProvLocationRepository locationRepository;
	@Autowired
	private org.ligoj.app.plugin.prov.dao.ProvConfigurationRepository provConfigurationRepository;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class<?>[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class },
				StandardCharsets.UTF_8);
		persistEntities("csv/database", new Class<?>[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8);
	}

	/**
	 * An {@link ImportCatalogResource} whose task operations run in the current (test) transaction instead of a new
	 * one, so the uncommitted test data is visible.
	 */
	static class TestImportCatalogResource extends ImportCatalogResource {

		@Override
		public ImportCatalogStatus startTask(final String lockedId, final Consumer<ImportCatalogStatus> initializer) {
			return startTaskInternal(lockedId, initializer);
		}

		@Override
		public ImportCatalogStatus nextStep(final String lockedId, final Consumer<ImportCatalogStatus> stepper) {
			return nextStepInternal(lockedId, stepper);
		}

		@Override
		public ImportCatalogStatus endTask(final String lockedId, final boolean failed) {
			return endTaskInternal(lockedId, failed, t -> {
				// Nothing to do by default
			});
		}

		@Override
		public ImportCatalogStatus endTask(final String lockedId, final boolean failed,
				final Consumer<ImportCatalogStatus> finalizer) {
			return endTaskInternal(lockedId, failed, finalizer);
		}
	}

	@Test
	void updateCatalog() throws Exception {
		final ImportCatalogResource resource = new TestImportCatalogResource() {

			@Override
			public ImportCatalogStatus getTask(final String node) {
				return getTaskRepository().findBy("locked.id", node);
			}

		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = mock(ServicePluginLocator.class);
		final var service = mock(ImportCatalogService.class);
		when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

		final var status = resource.updateCatalog("service:prov:test:account", false);
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
		Assertions.assertFalse(resource.getTask("service:prov:test").isFinished());
		Thread.sleep(100);
		Mockito.verify(service).updateCatalog("service:prov:test", false);
	}

	@Test
	void cancelNotExistNode() {
		final var resource = newResource();
		Assertions.assertEquals("read-only-node", Assertions
				.assertThrows(BusinessException.class, () -> resource.cancel("service:prov:any")).getMessage());
	}

	@Test
	void cancelNotVisible() {
		initSpringSecurityContext("any");
		final var resource = newResource();
		Assertions.assertEquals("read-only-node", Assertions
				.assertThrows(BusinessException.class, () -> resource.cancel("service:prov:test")).getMessage());
	}

	@Test
	void cancelNoStartedTask() {
		final var resource = new TestImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		final var status = newStatus();
		status.setEnd(new Date());
		Assertions.assertEquals("Already finished", Assertions
				.assertThrows(BusinessException.class, () -> resource.cancel("service:prov:test")).getMessage());
	}

	@Test
	void cancel() {
		final var resource = newResource();
		resource.cancel("service:prov:test");
		Assertions.assertTrue(resource.getTask("service:prov:test").isFailed());
	}

	@Test
	void updateCatalogSynchronous() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final var resource = newResource();
		final var service = mock(ImportCatalogService.class);
		resource.updateCatalog(service, "service:prov:test");

		final var status = repository.findBy("locked.id", "service:prov:test");
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNotNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertNull(status.getPhase());
		Assertions.assertEquals(0, status.getWorkload());
		Assertions.assertTrue(status.isFinished());
		Assertions.assertFalse(status.isFailed());
		Assertions.assertNotEquals(0, status.getLastSuccess().getTime());
		Assertions.assertEquals(124, status.getNbPrices().intValue());
		Assertions.assertEquals(116, status.getNbCo2Prices().intValue());
		Assertions.assertEquals(23, status.getNbTypes().intValue()); // 13 + 3 + 6 storage
		Assertions.assertEquals(4, status.getNbLocations().intValue());
		Mockito.verify(service).updateCatalog("service:prov:test", false);
	}

	@Test
	void updateCatalogSynchronousFailed() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final var resource = newResource();
		final var service = mock(ImportCatalogService.class);
		Mockito.doThrow(new IllegalStateException()).when(service).updateCatalog("service:prov:test", false);

		resource.updateCatalog(service, "service:prov:test");
		assertFailed(service);
	}

	@Test
	void updateCatalogSynchronousFailedWithError() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final var resource = newResource();
		final var service = mock(ImportCatalogService.class);
		Mockito.doThrow(new AssertionError("my-assert")).when(service).updateCatalog("service:prov:test", false);

		Assertions.assertThrows(AssertionError.class, () -> resource.updateCatalog(service, "service:prov:test"));
		assertFailed(service);
	}

	private void assertFailed(final ImportCatalogService service) throws Exception {
		final var status = repository.findBy("locked.id", "service:prov:test");
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNotNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
		Assertions.assertTrue(status.isFinished());
		Assertions.assertTrue(status.isFailed());
		Assertions.assertEquals(0, status.getLastSuccess().getTime());
		Assertions.assertEquals(0, status.getNbPrices().intValue());
		Assertions.assertEquals(0, status.getNbCo2Prices().intValue());
		Assertions.assertEquals(0, status.getNbTypes().intValue());
		Assertions.assertEquals(0, status.getNbLocations().intValue());
		Mockito.verify(service).updateCatalog("service:prov:test", false);
	}

	private ImportCatalogResource newResource() {
		final var resource = new TestImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		newStatus();
		return resource;
	}

	@Test
	void getConfiguration() {
		final var resource = newResource();
		resource.update(new CatalogEditionVo("service:prov:x", "region-1",
				java.util.Map.of("service:prov:x:regions", "region-.*")));

		final var vo = resource.getConfiguration("service:prov:x",
				java.util.List.of("service:prov:x:regions", "service:prov:x:os"));
		Assertions.assertEquals("region-1", vo.getDefaultLocation());
		// Full location objects (country, coordinates, ...) so the UI can render the flag, ordered by name
		Assertions.assertEquals(java.util.List.of("region-1", "region-3"),
				vo.getLocations().stream().map(ProvLocation::getName).toList());
		Assertions.assertEquals(java.util.Map.of("service:prov:x:regions", "region-.*"), vo.getProperties());
	}

	@Test
	void getConfigurationNotOwnedProperty() {
		final var resource = newResource();
		final var names = java.util.List.of("service:prov:other:regions");
		Assertions.assertThrows(ValidationJsonException.class,
				() -> resource.getConfiguration("service:prov:x", names));
	}

	@Test
	void updateNotOwnedProperty() {
		final var resource = newResource();
		final var vo = new CatalogEditionVo("service:prov:x", null,
				java.util.Map.of("service:prov:other:secret", "value"));
		Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(vo));
	}

	@Test
	void updateDeleteProperty() {
		final var resource = newResource();
		resource.update(new CatalogEditionVo("service:prov:x", null,
				java.util.Map.of("service:prov:x:regions", "region-.*")));
		resource.update(new CatalogEditionVo("service:prov:x", null, java.util.Map.of("service:prov:x:regions", "")));
		Assertions.assertTrue(resource
				.getConfiguration("service:prov:x", java.util.List.of("service:prov:x:regions")).getProperties()
				.isEmpty());
	}

	@Test
	void ensureDefaultLocation() {
		final var resource = newResource();
		resource.ensureDefaultLocation("service:prov:x");
		Assertions.assertEquals("region-1",
				resource.getConfiguration("service:prov:x", null).getDefaultLocation());

		// A defined default location is not overridden
		resource.update(new CatalogEditionVo("service:prov:x", "region-3", null));
		resource.ensureDefaultLocation("service:prov:x");
		Assertions.assertEquals("region-3",
				resource.getConfiguration("service:prov:x", null).getDefaultLocation());
	}

	@Test
	void ensureDefaultLocationNoLocation() {
		final var resource = newResource();
		// No available location for this node: no default location assigned, no configuration created
		resource.ensureDefaultLocation("service:prov:test2");
		Assertions.assertTrue(provConfigurationRepository.findById("service:prov:test2").isEmpty());
	}

	private ImportCatalogStatus newStatus() {
		final var status = new ImportCatalogStatus();
		status.setLastSuccess(new Date(0));
		status.setAuthor(DEFAULT_USER);
		status.setNbPrices(0);
		status.setNbTypes(0);
		status.setNbLocations(0);
		status.setNbCo2Prices(0);
		status.setStart(new Date());
		status.setLocked(nodeRepository.findOne("service:prov:test"));
		repository.saveAndFlush(status);
		return status;
	}

	@Test
	void updateCatalogFailed() throws Exception {
		final ImportCatalogResource resource = new TestImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = mock(ServicePluginLocator.class);
		final var service = mock(ImportCatalogService.class);
		when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);
		Mockito.doThrow(new IOException()).when(service).updateCatalog("service:prov:test", false);

		final var status = resource.updateCatalog("service:prov:test:account", false);
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
	}

	@Test
	void findAllNotVisible() {
		initSpringSecurityContext("any");
		final var resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		Assertions.assertEquals(0, resource.findAll().size());
	}

	@Test
	void findAllError() {
		final var resource = newResource();

		// Add not updatable provider node
		final var node = new Node();
		node.setId("service:prov:any");
		node.setName("Cannot import");
		node.setRefined(nodeRepository.findOneExpected("service:prov"));
		nodeRepository.saveAndFlush(node);

		var catalogs = resource.findAll();
		var location = locationRepository.findByName(catalogs.get(2).getNode().getId(), "region-1").getId();
		var catalogsVoError = new CatalogEditionVo();
		catalogsVoError.setDefaultLocation("region-1");
		catalogsVoError.setNode(node.getId());
		Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(catalogsVoError));
	}

	@Test
	void findAll() {
		final var resource = newResource();

		// Add importable provider
		resource.locator = mock(ServicePluginLocator.class);
		final var service = mock(ImportCatalogService.class);
		when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

		// Add not updatable provider node
		final var notImportNode = new Node();
		notImportNode.setId("service:prov:any");
		notImportNode.setName("Cannot import");
		notImportNode.setRefined(nodeRepository.findOneExpected("service:prov"));
		nodeRepository.saveAndFlush(notImportNode);

		// Initialize and update catalog
		var catalogs = resource.findAll();
		Assertions.assertEquals(3, catalogs.size());
		Assertions.assertNull(catalogs.getFirst().getDefaultLocation());
		Assertions.assertNull(catalogs.get(1).getDefaultLocation());
		Assertions.assertNull(catalogs.get(2).getDefaultLocation());
		var node = catalogs.get(2).getNode().getId();
		var catalogsVo = new CatalogEditionVo(node, "region-1", null);
		resource.update(catalogsVo);
		catalogs = resource.findAll();
		Assertions.assertEquals("region-1", catalogs.get(2).getDefaultLocation());

		// This provider does not support catalog update
		Assertions.assertEquals(0, catalogs.getFirst().getStatus().getNbPrices().intValue());
		Assertions.assertNull(catalogs.getFirst().getStatus().getEnd());
		Assertions.assertNull(catalogs.getFirst().getStatus().getStart());
		Assertions.assertEquals("service:prov:any", catalogs.getFirst().getNode().getId());
		Assertions.assertFalse(catalogs.getFirst().isCanImport());
		Assertions.assertEquals(0, catalogs.getFirst().getNbQuotes());
		Assertions.assertNull(catalogs.getFirst().getDefaultLocation());

		// This provider supports catalog update
		Assertions.assertNotNull(catalogs.get(1).getStatus());
		Assertions.assertEquals("service:prov:test", catalogs.get(1).getNode().getId());
		Assertions.assertTrue(catalogs.get(1).isCanImport());
		Assertions.assertEquals(2, catalogs.get(1).getNbQuotes());
		Assertions.assertNull(catalogs.get(1).getDefaultLocation());

		// This provider does not support catalog update
		Assertions.assertEquals("service:prov:x", catalogs.get(2).getNode().getId());
		Assertions.assertFalse(catalogs.get(2).isCanImport());
		Assertions.assertNull(catalogs.get(2).getStatus().getEnd());
		Assertions.assertNull(catalogs.get(2).getStatus().getStart());
		Assertions.assertEquals(1, catalogs.get(2).getNbQuotes());
		Assertions.assertEquals("region-1", catalogs.get(2).getDefaultLocation());

		final var status = catalogs.get(1).getStatus();
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
	}

}
