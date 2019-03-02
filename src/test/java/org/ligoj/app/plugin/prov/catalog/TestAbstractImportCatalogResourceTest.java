/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test class of {@link AbstractImportCatalogResource}
 */
public class TestAbstractImportCatalogResourceTest extends AbstractImportCatalogResource {

	private AbstractImportCatalogResource resource;

	@BeforeEach
	public void init() {
		resource = new AbstractImportCatalogResource() {
			// Nothing
		};
		resource.objectMapper = new ObjectMapper();

		// Coverage only, required for inheriting provisioning plug-in
		resource.getImportCatalogResource();
		resource.setImportCatalogResource(null);
		importCatalogResource = Mockito.mock(ImportCatalogResource.class);
		objectMapper = new ObjectMapper();
	}

	@Test
	public void getRate() throws IOException {
		check("test-resource", Rate.BEST);
	}

	@Test
	public void initContext() {
		nodeRepository = Mockito.mock(NodeRepository.class);
		configuration = Mockito.mock(ConfigurationResource.class);
		Mockito.when(nodeRepository.findOneExpected("service:prov:sample")).thenReturn(new Node());
		Mockito.when(configuration.get(CONF_HOURS_MONTH, DEFAULT_HOURS_MONTH)).thenReturn(1);
		final AbstractUpdateContext context = new AbstractUpdateContext() {
			// Nothing
		};
		initContext(context, "service:prov:sample");
		Assertions.assertNotNull(context.getNode());
		Assertions.assertEquals(1, context.getHoursMonth());
	}

	@Test
	public void round3Decimals() {
		Assertions.assertEquals(1.235, super.round3Decimals(1.2348), 0.001);
	}

	@Test
	public void toMap() throws IOException {
		Assertions.assertEquals("WORST", super.toMap("rate-test-resource.json", MAP_STR).get("b1"));
	}

	@Test
	public void toPercentNull() {
		Assertions.assertNull(super.toPercent("some"));
	}

	@Test
	public void toPercent() {
		Assertions.assertEquals(1.23d, super.toPercent("1.23%"), 0.001);
	}

	@Test
	public void isEnabledRegion() {
		final AbstractUpdateContext context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final ProvLocation location = new ProvLocation();
		location.setName("name");
		Assertions.assertTrue(isEnabledRegion(context, location));
	}

	@Test
	public void installRegion() {
		final AbstractUpdateContext context = newContext();
		final Node node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		context.setRegions(new HashMap<String, ProvLocation>());

		final ProvLocation oldRegion = new ProvLocation();
		oldRegion.setContinentM49(250);
		context.getMapRegionToName().put("newRegion", oldRegion);
		locationRepository = Mockito.mock(ProvLocationRepository.class);
		final ProvLocation installRegion = installRegion(context, "newRegion");
		Assertions.assertEquals("newRegion", installRegion.getName());
		Assertions.assertEquals(250, installRegion.getContinentM49().intValue());
		installRegion.setLatitude(1D);
		final ProvLocation installRegion2 = installRegion(context, "newRegion");
		Assertions.assertSame(installRegion2, installRegion);
	}

	@Test
	public void getRegionByHumanNameEmpty() {
		final AbstractUpdateContext context = newContext();
		context.setRegions(new HashMap<String, ProvLocation>());
		Assertions.assertNull(getRegionByHumanName(context, "any"));
	}

	@Test
	public void getRegionByHumanNameNotEnabled() {
		final AbstractUpdateContext context = newContext();
		final ProvLocation oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		context.setValidRegion(Pattern.compile(".*"));
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertNull(getRegionByHumanName(context, "newRegion"));
	}

	@Test
	public void getRegionByHumanNotFound() {
		final AbstractUpdateContext context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final ProvLocation oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		oldRegion.setDescription("newRegion");
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertNull(getRegionByHumanName(context, "-not-found-"));
	}

	@Test
	public void getRegionByHumanName() {
		final AbstractUpdateContext context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final ProvLocation oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		oldRegion.setDescription("newRegion");
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertEquals("newRegion", getRegionByHumanName(context, "newRegion").getName());
	}

	@Test
	public void getWorkload() {
		Assertions.assertEquals(0, getWorkload(null));
	}

	@Test
	public void nextStepIgnore() {
		final AbstractUpdateContext context = newContext();
		final Node node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		nextStep(context, "location", 1);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void nextStep() {
		final AbstractUpdateContext context = newContext();
		final Node node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		final ImportCatalogStatus status = new ImportCatalogStatus();

		Mockito.doAnswer(invocation -> {
			((Consumer<ImportCatalogStatus>) invocation.getArguments()[1]).accept(status);
			return null;
		}).when(importCatalogResource).nextStep(Mockito.any(), Mockito.any());
		nextStep(context, "location", 1);
		Assertions.assertEquals("location", status.getLocation());
	}

	@Test
	public void getRateNoDefault() throws IOException {
		check("test-resource-no-default", Rate.MEDIUM);
	}

	@Test
	public void saveAsNeededSame() {
		final ProvInstancePrice entity = new ProvInstancePrice();
		entity.setCode("old");
		final Consumer<ProvInstancePrice> consumer = p -> {
			p.setCode("-nerver-called-");
		};
		saveAsNeeded(entity, 1, 1, null, consumer);
		Assertions.assertEquals("old", entity.getCode());
	}

	@Test
	public void saveAsNeeded() {
		final ProvStoragePrice entity = new ProvStoragePrice();
		entity.setCostGb(2d);
		final Consumer<ProvStoragePrice> consumer = p -> {
			p.setCode("code");
		};
		saveAsNeeded(entity, 1, consumer);
		Assertions.assertEquals("code", entity.getCode());
	}

	private void check(final String file, final Rate def) throws IOException {
		resource.initRate(file);
		Assertions.assertEquals(Rate.LOW, resource.getRate(file, "a.micro"));
		Assertions.assertEquals(Rate.LOW, resource.getRate(file, "a1.micro"));
		Assertions.assertEquals(Rate.LOW, resource.getRate(file, "a1"));
		Assertions.assertEquals(Rate.WORST, resource.getRate(file, "b1"));
		Assertions.assertEquals(Rate.WORST, resource.getRate(file, "b1.large"));
		Assertions.assertEquals(Rate.GOOD, resource.getRate(file, "c2.large"));

		// No match, "default"
		Assertions.assertEquals(def, resource.getRate(file, "c"));
		Assertions.assertEquals(def, resource.getRate(file, "c2"));
		Assertions.assertEquals(def, resource.getRate(file, "b"));
		Assertions.assertEquals(def, resource.getRate(file, "x"));
		Assertions.assertEquals(def, resource.getRate(file, "x1"));
		Assertions.assertEquals(def, resource.getRate(file, "x1.micro"));
	}

	@Test
	public void toVmOs() {
		Assertions.assertEquals(VmOs.WINDOWS, toVmOs("windows"));
	}

	@Test
	public void isEnabledOs() {
		final AbstractUpdateContext context = newContext();
		context.setValidOs(Pattern.compile("(LINUX|RH.*)"));
		Assertions.assertFalse(isEnabledOs(context, VmOs.WINDOWS));
		Assertions.assertTrue(isEnabledOs(context, VmOs.LINUX));
		Assertions.assertTrue(isEnabledOs(context, VmOs.RHEL));
	}

	@Test
	public void isEnabledType() {
		final AbstractUpdateContext context = newContext();
		context.setValidInstanceType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledType(context, "axr"));
		Assertions.assertTrue(isEnabledType(context, "abr"));
	}

	@Test
	public void isEnabledDatabase() {
		final AbstractUpdateContext context = newContext();
		context.setValidDatabaseType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledDatabase(context, "axr"));
		Assertions.assertTrue(isEnabledDatabase(context, "abr"));
	}

	/**
	 * Only there for coverage and API contracts.
	 */
	@Test
	public void bean() {
		final AbstractUpdateContext context = newContext();
		context.setStorageTypes(null);
		context.setStorageTypesMerged(null);
		context.setPriceTerms(null);
		context.setPrevious(null);
		context.setPreviousDatabase(null);
		context.setPreviousStorage(null);
		context.setInstanceTypes(null);
		context.setDatabaseTypes(null);
		context.getDatabaseTypes();
		context.getInstanceTypes();
		context.getInstanceTypesMerged();
		context.getPrevious();
		context.getPreviousDatabase();
		context.getPreviousStorage();
		context.getPrevious();
		context.getStorageTypes();
		context.getStorageTypesMerged();
		context.getPriceTerms();
	}

	private AbstractUpdateContext newContext() {
		final AbstractUpdateContext context = new AbstractUpdateContext() {
		};
		return context;
	}
}
