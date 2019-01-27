/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import javax.transaction.Transactional;
import javax.ws.rs.PathParam;

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
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
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
public class ImportCatalogResourceTest extends AbstractAppTest {

	@Autowired
	private ImportCatalogStatusRepository repository;
	@Autowired
	private NodeRepository nodeRepository;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class,
				ProvQuote.class, ProvStorageType.class, ProvStoragePrice.class, ProvInstancePriceTerm.class,
				ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
	}

	@Test
	public void updateCatalog() throws Exception {
		final ImportCatalogResource resource = new ImportCatalogResource() {

			@Override
			public ImportCatalogStatus getTask(@PathParam("node") final String node) {
				return getTaskRepository().findBy("locked.id", node);
			}

		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

		final ImportCatalogStatus status = resource.updateCatalog("service:prov:test:account");
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
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	@Test
	public void cancelNotExistNode() {
		Assertions.assertEquals("read-only-node", Assertions
				.assertThrows(BusinessException.class, () -> newResource().cancel("service:prov:any")).getMessage());
	}

	@Test
	public void cancelNotVisible() {
		initSpringSecurityContext("any");
		Assertions.assertEquals("read-only-node", Assertions
				.assertThrows(BusinessException.class, () -> newResource().cancel("service:prov:test")).getMessage());
	}

	@Test
	public void cancelNoStartedTask() {
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		final ImportCatalogStatus status = newStatus();
		status.setEnd(new Date());
		Assertions.assertEquals("Already finished", Assertions
				.assertThrows(BusinessException.class, () -> resource.cancel("service:prov:test")).getMessage());
	}

	@Test
	public void cancel() {
		final ImportCatalogResource resource = newResource();
		resource.cancel("service:prov:test");
		Assertions.assertTrue(resource.getTask("service:prov:test").isFailed());
	}

	@Test
	public void updateCatalogSynchronous() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final ImportCatalogResource resource = newResource();
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		resource.updateCatalog(service, "service:prov:test");

		final ImportCatalogStatus status = repository.findBy("locked.id", "service:prov:test");
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
		Assertions.assertEquals(113, status.getNbInstancePrices().intValue()); // 103 + 10
		Assertions.assertEquals(16, status.getNbInstanceTypes().intValue()); // 13 + 3
		Assertions.assertEquals(3, status.getNbLocations().intValue());
		Assertions.assertEquals(6, status.getNbStorageTypes().intValue()); // 4 + 2
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	@Test
	public void updateCatalogSynchronousFailed() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final ImportCatalogResource resource = newResource();
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.doThrow(new IllegalStateException()).when(service).updateCatalog("service:prov:test");

		resource.updateCatalog(service, "service:prov:test");
		assertFailed(service);
	}

	@Test
	public void updateCatalogSynchronousFailedWithError() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final ImportCatalogResource resource = newResource();
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.doThrow(new AssertionError("my-assert")).when(service).updateCatalog("service:prov:test");

		Assertions.assertThrows(AssertionError.class, () -> resource.updateCatalog(service, "service:prov:test"));
		assertFailed(service);
	}

	private void assertFailed(final ImportCatalogService service) throws Exception {
		final ImportCatalogStatus status = repository.findBy("locked.id", "service:prov:test");
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
		Assertions.assertEquals(-1, status.getNbInstancePrices().intValue());
		Assertions.assertEquals(-1, status.getNbInstanceTypes().intValue());
		Assertions.assertEquals(-1, status.getNbLocations().intValue());
		Assertions.assertEquals(-1, status.getNbStorageTypes().intValue());
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	private ImportCatalogResource newResource() {
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		newStatus();
		return resource;
	}

	private ImportCatalogStatus newStatus() {
		final ImportCatalogStatus status = new ImportCatalogStatus();
		status.setLastSuccess(new Date(0));
		status.setAuthor(DEFAULT_USER);
		status.setNbInstancePrices(-1);
		status.setNbInstanceTypes(-1);
		status.setNbLocations(-1);
		status.setNbStorageTypes(-1);
		status.setStart(new Date());
		status.setLocked(nodeRepository.findOne("service:prov:test"));
		repository.saveAndFlush(status);
		return status;
	}

	@Test
	public void updateCatalogFailed() throws Exception {
		final ImportCatalogResource resource = new ImportCatalogResource() {
			@Override
			public ImportCatalogStatus nextStep(final String node, final Consumer<ImportCatalogStatus> stepper) {
				return super.nextStep(node, stepper);
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);

		// Replace the locator for the custom provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);
		Mockito.doThrow(new IOException()).when(service).updateCatalog("service:prov:test");

		final ImportCatalogStatus status = resource.updateCatalog("service:prov:test:account");
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
	}

	@Test
	public void findAllNotVisible() {
		initSpringSecurityContext("any");
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		Assertions.assertEquals(0, resource.findAll().size());
	}

	@Test
	public void findAll() {
		final ImportCatalogResource resource = newResource();

		// Add importable provider
		resource.locator = Mockito.mock(ServicePluginLocator.class);
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.when(resource.locator.getResource("service:prov:test", ImportCatalogService.class)).thenReturn(service);

		// Add not updatable provider node
		final Node notImportNode = new Node();
		notImportNode.setId("service:prov:any");
		notImportNode.setName("Cannot import");
		notImportNode.setRefined(nodeRepository.findOneExpected("service:prov"));
		nodeRepository.saveAndFlush(notImportNode);

		final List<CatalogVo> catalogs = resource.findAll();
		Assertions.assertEquals(3, catalogs.size());

		// This provider does not support catalog update
		Assertions.assertEquals(0, catalogs.get(0).getStatus().getNbInstancePrices().intValue());
		Assertions.assertNull(catalogs.get(0).getStatus().getEnd());
		Assertions.assertNull(catalogs.get(0).getStatus().getStart());
		Assertions.assertEquals("service:prov:any", catalogs.get(0).getNode().getId());
		Assertions.assertFalse(catalogs.get(0).isCanImport());
		Assertions.assertEquals(0, catalogs.get(0).getNbQuotes());

		// This provider supports catalog update
		Assertions.assertNotNull(catalogs.get(1).getStatus());
		Assertions.assertEquals("service:prov:test", catalogs.get(1).getNode().getId());
		Assertions.assertTrue(catalogs.get(1).isCanImport());
		Assertions.assertEquals(2, catalogs.get(1).getNbQuotes());

		// This provider does not support catalog update
		Assertions.assertEquals("service:prov:x", catalogs.get(2).getNode().getId());
		Assertions.assertFalse(catalogs.get(2).isCanImport());
		Assertions.assertNull(catalogs.get(2).getStatus().getEnd());
		Assertions.assertNull(catalogs.get(2).getStatus().getStart());
		Assertions.assertEquals(1, catalogs.get(2).getNbQuotes());

		final ImportCatalogStatus status = catalogs.get(1).getStatus();
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertNull(status.getEnd());
		Assertions.assertNull(status.getLocation());
		Assertions.assertEquals("service:prov:test", status.getLocked().getId());
		Assertions.assertNotNull(status.getStart());
		Assertions.assertEquals(0, status.getDone());
		Assertions.assertEquals(0, status.getWorkload());
	}

}
