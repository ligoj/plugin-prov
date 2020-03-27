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
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test class of {@link AbstractImportCatalogResource}
 */
public class TestAbstractImportCatalogResourceTest extends AbstractImportCatalogResource {

	private AbstractImportCatalogResource resource;

	@BeforeEach
	void init() {
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
	void getRate() throws IOException {
		check("test-resource", Rate.BEST);
	}

	@Test
	void initContext() {
		nodeRepository = Mockito.mock(NodeRepository.class);
		configuration = Mockito.mock(ConfigurationResource.class);
		Mockito.when(nodeRepository.findOneExpected("service:prov:sample")).thenReturn(new Node());
		Mockito.when(configuration.get(CONF_HOURS_MONTH, DEFAULT_HOURS_MONTH)).thenReturn(1);
		final AbstractUpdateContext context = new AbstractUpdateContext() {
			// Nothing
		};
		initContext(context, "service:prov:sample", true);
		Assertions.assertNotNull(context.getNode());
		Assertions.assertEquals(1, context.getHoursMonth());
		Assertions.assertTrue(context.isForce());
	}

	@Test
	void round3Decimals() {
		Assertions.assertEquals(1.235, super.round3Decimals(1.2348), 0.001);
	}

	@Test
	void toMap() throws IOException {
		Assertions.assertEquals("WORST", super.toMap("rate-test-resource.json", MAP_STR).get("b1"));
	}

	@Test
	void toPercentNull() {
		Assertions.assertNull(super.toPercent("some"));
	}

	@Test
	void toPercent() {
		Assertions.assertEquals(1.23d, super.toPercent("1.23%"), 0.001);
	}

	@Test
	void isEnabledRegion() {
		final var context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final var location = new ProvLocation();
		location.setName("name");
		Assertions.assertTrue(isEnabledRegion(context, location));
	}

	@Test
	void installRegion() {
		final var context = newContext();
		final var node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		context.setRegions(new HashMap<>());

		final var oldRegion = new ProvLocation();
		oldRegion.setContinentM49(250);
		context.getMapRegionToName().put("newRegion", oldRegion);
		locationRepository = Mockito.mock(ProvLocationRepository.class);
		final var installRegion = installRegion(context, "newRegion");
		Assertions.assertEquals("newRegion", installRegion.getName());
		Assertions.assertEquals(250, installRegion.getContinentM49().intValue());
		installRegion.setLatitude(1D);
		final var installRegion2 = installRegion(context, "newRegion");
		Assertions.assertSame(installRegion2, installRegion);
	}

	@Test
	void getRegionByHumanNameEmpty() {
		final var context = newContext();
		context.setRegions(new HashMap<>());
		Assertions.assertNull(getRegionByHumanName(context, "any"));
	}

	@Test
	void getRegionByHumanNameNotEnabled() {
		final var context = newContext();
		final var oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		context.setValidRegion(Pattern.compile(".*"));
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertNull(getRegionByHumanName(context, "newRegion"));
	}

	@Test
	void getRegionByHumanNotFound() {
		final var context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final var oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		oldRegion.setDescription("newRegion");
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertNull(getRegionByHumanName(context, "-not-found-"));
	}

	@Test
	void getRegionByHumanName() {
		final var context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final var oldRegion = new ProvLocation();
		oldRegion.setName("newRegion");
		oldRegion.setDescription("newRegion");
		context.setRegions(Collections.singletonMap("newRegion", oldRegion));
		Assertions.assertEquals("newRegion", getRegionByHumanName(context, "newRegion").getName());
	}

	@Test
	void getWorkload() {
		Assertions.assertEquals(0, getWorkload(null));
	}

	@Test
	void nextStepIgnore() {
		final var context = newContext();
		final var node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		nextStep(context, "location", 1);
	}

	@SuppressWarnings("unchecked")
	@Test
	void nextStepLocation() {
		final var context = newContext();
		final var node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		final var status = new ImportCatalogStatus();

		Mockito.doAnswer(invocation -> {
			((Consumer<ImportCatalogStatus>) invocation.getArguments()[1]).accept(status);
			return null;
		}).when(importCatalogResource).nextStep(Mockito.any(), Mockito.any());
		nextStep(context, "location", 1);
		Assertions.assertEquals("location", status.getLocation());
	}


	@SuppressWarnings("unchecked")
	@Test
	void nextStepPhase() {
		final var context = newContext();
		final var node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");
		context.setNode(node);
		final var status = new ImportCatalogStatus();

		Mockito.doAnswer(invocation -> {
			((Consumer<ImportCatalogStatus>) invocation.getArguments()[1]).accept(status);
			return null;
		}).when(importCatalogResource).nextStep(Mockito.any(), Mockito.any());
		nextStep(context, "phase");
		Assertions.assertEquals("phase", status.getPhase());
	}

	@Test
	void getRateNoDefault() throws IOException {
		check("test-resource-no-default", Rate.MEDIUM);
	}

	@Test
	void saveAsNeededSame() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-nerver-called-");
		saveAsNeeded(newContext(), entity, 1, 1, null, consumer);
		Assertions.assertEquals("old", entity.getCode());
	}

	@Test
	void saveAsNeededForce() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();
		newContext.setForce(true);

		// Force mode for same cost
		saveAsNeeded(newContext, entity, 1, 1, (cRound, c) -> {
			entity.setCost(cRound);
		}, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
		Assertions.assertEquals(1, entity.getCost());
	}

	@Test
	void saveAsNeeded() {
		final var entity = new ProvInstancePrice();
		entity.setCost(1d);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		saveAsNeeded(newContext(), entity, 2.013d, 2.01234d, (cRound, c) -> {
			entity.setCost(cRound);
		}, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
		Assertions.assertEquals(2.012d, entity.getCost());
	}

	@Test
	void saveAsNeededSameRound() {
		final var entity = new ProvInstancePrice();
		entity.setCost(1d);
		entity.setCode("old");
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-never-called-");
		saveAsNeeded(newContext(), entity, 2.012d, 2.01234d, (cRound, c) -> {
			entity.setCost(cRound);
		}, consumer);
		Assertions.assertEquals("old", entity.getCode());
		Assertions.assertEquals(1d, entity.getCost());
	}

	@Test
	void copyAsNeededForce() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		entity.setId(1);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();
		newContext.setForce(true);

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
	}

	@Test
	void copyAsNeededNew() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
	}

	@Test
	void copyAsNeededNo() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		entity.setId(1);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("old", entity.getCode());
	}

	@Test
	void saveAsNeededCostPeriod() {
		final var entity = new ProvInstancePrice();
		final var term = new ProvInstancePriceTerm();
		term.setPeriod(12d);
		entity.setTerm(term );
		entity.setCost(2d);
		entity.setCostPeriod(24d);
		@SuppressWarnings("unchecked")
		final RestRepository<ProvInstancePrice, Integer> repository = Mockito.mock(RestRepository.class);
		saveAsNeeded(newContext(), entity, 3, repository);
		Assertions.assertEquals(3, entity.getCost());
		Assertions.assertEquals(36, entity.getCostPeriod());
		Mockito.verify(repository).save(entity);
	}

	@Test
	void saveAsNeededCostGb() {
		final var entity = new ProvStoragePrice();
		entity.setCostGb(2d);
		@SuppressWarnings("unchecked")
		final RestRepository<ProvStoragePrice, Integer> repository = Mockito.mock(RestRepository.class);
		saveAsNeeded(newContext(), entity, 1, repository);
		Assertions.assertEquals(1, entity.getCostGb());
		Mockito.verify(repository).save(entity);
	}

	@Test
	void saveAsNeededCostGbNoChange() {
		final var entity = new ProvStoragePrice();
		entity.setCostGb(1d);
		@SuppressWarnings("unchecked")
		final RestRepository<ProvStoragePrice, Integer> repository = Mockito.mock(RestRepository.class);
		saveAsNeeded(newContext(), entity, 1, repository);
		Assertions.assertEquals(1, entity.getCostGb());
		Mockito.verify(repository, Mockito.never()).save(entity);
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
	void toVmOs() {
		Assertions.assertEquals(VmOs.WINDOWS, toVmOs("windows"));
	}

	@Test
	void isEnabledOs() {
		final var context = newContext();
		context.setValidOs(Pattern.compile("(LINUX|RH.*)"));
		Assertions.assertFalse(isEnabledOs(context, VmOs.WINDOWS));
		Assertions.assertTrue(isEnabledOs(context, VmOs.LINUX));
		Assertions.assertTrue(isEnabledOs(context, VmOs.RHEL));
	}

	@Test
	void isEnabledType() {
		final var context = newContext();
		context.setValidInstanceType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledType(context, "axr"));
		Assertions.assertTrue(isEnabledType(context, "abr"));
	}

	@Test
	void isEnabledEngine() {
		final var context = newContext();
		context.setValidDatabaseEngine(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledEngine(context, "axr"));
		Assertions.assertTrue(isEnabledEngine(context, "abr"));
	}

	@Test
	void isEnabledDatabase() {
		final var context = newContext();
		context.setValidDatabaseType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledDatabase(context, "axr"));
		Assertions.assertTrue(isEnabledDatabase(context, "abr"));
	}

	/**
	 * Only there for coverage and API contracts.
	 */
	@Test
	void bean() {
		final var context = newContext();
		context.getDatabaseTypes();
		context.setDatabaseTypes(null);
		context.getInstanceTypes();
		context.setInstanceTypes(null);
		context.getSupportTypes();
		context.setSupportTypes(null);
		context.getPrevious();
		context.setPrevious(null);
		context.getPreviousDatabase();
		context.setPreviousDatabase(null);
		context.getPreviousStorage();
		context.setPreviousStorage(null);
		context.getPreviousSupport();
		context.setPreviousSupport(null);
		context.getStorageTypes();
		context.setStorageTypes(null);
		context.getPriceTerms();
		context.setPriceTerms(null);
	}

	private AbstractUpdateContext newContext() {
		return new AbstractUpdateContext() {
		};
	}
}
