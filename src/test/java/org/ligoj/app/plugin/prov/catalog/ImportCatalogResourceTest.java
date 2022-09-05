/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Consumer;

import javax.transaction.Transactional;

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
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.resource.ServicePluginLocator;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
						ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
						ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
	}

	@Test
	void updateCatalog() throws Exception {
		final ImportCatalogResource resource = new ImportCatalogResource() {

			@Override
			public ImportCatalogStatus getTask(final String node) {
				return getTaskRepository().findBy("locked.id", node);
			}

		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final var service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

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
		final var resource = new ImportCatalogResource();
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
		final var service = Mockito.mock(ImportCatalogService.class);
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
		final var service = Mockito.mock(ImportCatalogService.class);
		Mockito.doThrow(new IllegalStateException()).when(service).updateCatalog("service:prov:test", false);

		resource.updateCatalog(service, "service:prov:test");
		assertFailed(service);
	}

	@Test
	void updateCatalogSynchronousFailedWithError() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final var resource = newResource();
		final var service = Mockito.mock(ImportCatalogService.class);
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
		final var resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		newStatus();
		return resource;
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
		final ImportCatalogResource resource = new ImportCatalogResource() {
			@Override
			public ImportCatalogStatus nextStep(final String node, final Consumer<ImportCatalogStatus> stepper) {
				return super.nextStep(node, stepper);
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final var service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);
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
		catalogsVoError.setPreferredLocation(location);
		catalogsVoError.setNode(node.getId());
		Assertions.assertThrows(ValidationJsonException.class, () -> resource.update(catalogsVoError));
	}

	@Test
	void findAll() {
		final var resource = newResource();

		// Add importable provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final var service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

		// Add not updatable provider node
		final var notImportNode = new Node();
		notImportNode.setId("service:prov:any");
		notImportNode.setName("Cannot import");
		notImportNode.setRefined(nodeRepository.findOneExpected("service:prov"));
		nodeRepository.saveAndFlush(notImportNode);

		// Initialize and update catalog
		var catalogs = resource.findAll();
		Assertions.assertEquals(3, catalogs.size());
		Assertions.assertNull(catalogs.get(0).getPreferredLocation());
		Assertions.assertNull(catalogs.get(1).getPreferredLocation());
		Assertions.assertNull(catalogs.get(2).getPreferredLocation());
		var node = catalogs.get(2).getNode().getId();
		var location = locationRepository.findByName(node, "region-1").getId();
		var catalogsVo = new CatalogEditionVo(location, node);
		resource.update(catalogsVo);
		catalogs = resource.findAll();
		Assertions.assertEquals(location, catalogs.get(2).getPreferredLocation().getId());

		// This provider does not support catalog update
		Assertions.assertEquals(0, catalogs.get(0).getStatus().getNbPrices().intValue());
		Assertions.assertNull(catalogs.get(0).getStatus().getEnd());
		Assertions.assertNull(catalogs.get(0).getStatus().getStart());
		Assertions.assertEquals("service:prov:any", catalogs.get(0).getNode().getId());
		Assertions.assertFalse(catalogs.get(0).isCanImport());
		Assertions.assertEquals(0, catalogs.get(0).getNbQuotes());
		Assertions.assertEquals(null, catalogs.get(0).getPreferredLocation());

		// This provider supports catalog update
		Assertions.assertNotNull(catalogs.get(1).getStatus());
		Assertions.assertEquals("service:prov:test", catalogs.get(1).getNode().getId());
		Assertions.assertTrue(catalogs.get(1).isCanImport());
		Assertions.assertEquals(2, catalogs.get(1).getNbQuotes());
		Assertions.assertEquals(null, catalogs.get(1).getPreferredLocation());

		// This provider does not support catalog update
		Assertions.assertEquals("service:prov:x", catalogs.get(2).getNode().getId());
		Assertions.assertFalse(catalogs.get(2).isCanImport());
		Assertions.assertNull(catalogs.get(2).getStatus().getEnd());
		Assertions.assertNull(catalogs.get(2).getStatus().getStart());
		Assertions.assertEquals(1, catalogs.get(2).getNbQuotes());
		Assertions.assertEquals("region-1", catalogs.get(2).getPreferredLocation().getName());

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
