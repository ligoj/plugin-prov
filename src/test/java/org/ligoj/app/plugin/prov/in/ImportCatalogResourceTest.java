package org.ligoj.app.plugin.prov.in;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import javax.transaction.Transactional;
import javax.ws.rs.PathParam;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ImportCatalogStatusRepository;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
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
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Test class of {@link ImportCatalogResource}
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ImportCatalogResourceTest extends AbstractAppTest {

	@Autowired
	private ImportCatalogStatusRepository repository;
	@Autowired
	private NodeRepository nodeRepository;

	@Before
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
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
		Assert.assertEquals(DEFAULT_USER, status.getAuthor());
		Assert.assertNull(status.getEnd());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals("service:prov:test", status.getLocked().getId());
		Assert.assertNotNull(status.getStart());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals(0, status.getDone());
		Assert.assertEquals(0, status.getWorkload());
		Assert.assertFalse(resource.getTask("service:prov:test").isFinished());
		Thread.sleep(100);
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	@Test(expected = BusinessException.class)
	public void cancelNotExistNode() {
		newResource().cancel("service:prov:any");
	}

	@Test(expected = BusinessException.class)
	public void cancelNotVisible() {
		initSpringSecurityContext("any");
		newResource().cancel("service:prov:test");
	}

	@Test(expected = BusinessException.class)
	public void cancelNoStartedTask() {
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		final ImportCatalogStatus status = newStatus();
		status.setEnd(new Date());
		resource.cancel("service:prov:test");
	}

	@Test
	public void cancel() {
		final ImportCatalogResource resource = newResource();
		resource.cancel("service:prov:test");
		Assert.assertTrue(resource.getTask("service:prov:test").isFailed());
	}

	@Test
	public void updateCatalogSynchronous() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final ImportCatalogResource resource = newResource();
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		resource.updateCatalog(service, "service:prov:test");

		final ImportCatalogStatus status = repository.findBy("locked.id", "service:prov:test");
		Assert.assertEquals(DEFAULT_USER, status.getAuthor());
		Assert.assertNotNull(status.getEnd());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals("service:prov:test", status.getLocked().getId());
		Assert.assertNotNull(status.getStart());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals(0, status.getDone());
		Assert.assertNull(status.getPhase());
		Assert.assertEquals(0, status.getWorkload());
		Assert.assertTrue(status.isFinished());
		Assert.assertFalse(status.isFailed());
		Assert.assertNotEquals(0, status.getLastSuccess().getTime());
		Assert.assertEquals(101, status.getNbInstancePrices().intValue());
		Assert.assertEquals(13, status.getNbInstanceTypes().intValue());
		Assert.assertEquals(3, status.getNbLocations().intValue());
		Assert.assertEquals(3, status.getNbStorageTypes().intValue());
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	@Test
	public void updateCatalogSynchronousFailed() throws Exception {
		initSpringSecurityContext(DEFAULT_USER);
		final ImportCatalogResource resource = newResource();
		final ImportCatalogService service = Mockito.mock(ImportCatalogService.class);
		Mockito.doThrow(new IllegalStateException()).when(service).updateCatalog("service:prov:test");

		resource.updateCatalog(service, "service:prov:test");
		final ImportCatalogStatus status = repository.findBy("locked.id", "service:prov:test");
		Assert.assertEquals(DEFAULT_USER, status.getAuthor());
		Assert.assertNotNull(status.getEnd());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals("service:prov:test", status.getLocked().getId());
		Assert.assertNotNull(status.getStart());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals(0, status.getDone());
		Assert.assertEquals(0, status.getWorkload());
		Assert.assertTrue(status.isFinished());
		Assert.assertTrue(status.isFailed());
		Assert.assertEquals(0, status.getLastSuccess().getTime());
		Assert.assertEquals(-1, status.getNbInstancePrices().intValue());
		Assert.assertEquals(-1, status.getNbInstanceTypes().intValue());
		Assert.assertEquals(-1, status.getNbLocations().intValue());
		Assert.assertEquals(-1, status.getNbStorageTypes().intValue());
		Mockito.verify(service).updateCatalog("service:prov:test");
	}

	private ImportCatalogResource newResource() {
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		newStatus();
		return resource;
	}

	private ImportCatalogStatus newStatus() {
		ImportCatalogStatus status = new ImportCatalogStatus();
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
		Assert.assertEquals(DEFAULT_USER, status.getAuthor());
		Assert.assertNull(status.getEnd());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals("service:prov:test", status.getLocked().getId());
		Assert.assertNotNull(status.getStart());
		Assert.assertEquals(0, status.getDone());
		Assert.assertEquals(0, status.getWorkload());
	}

	@Test
	public void findAllNotVisible() {
		initSpringSecurityContext("any");
		final ImportCatalogResource resource = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(resource);
		Assert.assertEquals(0, resource.findAll().size());
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
		Assert.assertEquals(3, catalogs.size());

		// This provider does not support catalog update
		Assert.assertEquals(0, catalogs.get(0).getStatus().getNbInstancePrices().intValue());
		Assert.assertNull(catalogs.get(0).getStatus().getEnd());
		Assert.assertNull(catalogs.get(0).getStatus().getStart());
		Assert.assertEquals("service:prov:any", catalogs.get(0).getNode().getId());
		Assert.assertFalse(catalogs.get(0).isCanImport());
		Assert.assertEquals(0, catalogs.get(0).getNbQuotes());

		// This provider supports catalog update
		Assert.assertNotNull(catalogs.get(1).getStatus());
		Assert.assertEquals("service:prov:test", catalogs.get(1).getNode().getId());
		Assert.assertTrue(catalogs.get(1).isCanImport());
		Assert.assertEquals(2, catalogs.get(1).getNbQuotes());

		// This provider does not support catalog update
		Assert.assertEquals("service:prov:x", catalogs.get(2).getNode().getId());
		Assert.assertFalse(catalogs.get(2).isCanImport());
		Assert.assertNull(catalogs.get(2).getStatus().getEnd());
		Assert.assertNull(catalogs.get(2).getStatus().getStart());
		Assert.assertEquals(1, catalogs.get(2).getNbQuotes());

		final ImportCatalogStatus status = catalogs.get(1).getStatus();
		Assert.assertEquals(DEFAULT_USER, status.getAuthor());
		Assert.assertNull(status.getEnd());
		Assert.assertNull(status.getLocation());
		Assert.assertEquals("service:prov:test", status.getLocked().getId());
		Assert.assertNotNull(status.getStart());
		Assert.assertEquals(0, status.getDone());
		Assert.assertEquals(0, status.getWorkload());
	}

}
