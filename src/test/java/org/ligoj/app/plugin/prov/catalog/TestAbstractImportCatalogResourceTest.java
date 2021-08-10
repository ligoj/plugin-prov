/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Test class of {@link AbstractImportCatalogResource}
 */
class TestAbstractImportCatalogResourceTest extends AbstractImportCatalogResource {

	private AbstractImportCatalogResource resource;

	/**
	 * Only there for coverage and API contracts.
	 */
	@Test
	void bean() {
		final var context = newContext();
		context.getDatabaseTypes();
		context.setDatabaseTypes(new HashMap<>());
		context.getInstanceTypes();
		context.setInstanceTypes(new HashMap<>());
		context.getContainerTypes();
		context.setContainerTypes(new HashMap<>());
		context.getSupportTypes();
		context.setSupportTypes(new HashMap<>());
		context.getPrevious();
		context.setPrevious(new HashMap<>());
		context.getPreviousDatabase();
		context.setPreviousDatabase(new HashMap<>());
		context.getPreviousContainer();
		context.setPreviousContainer(new HashMap<>());
		context.getPreviousStorage();
		context.setPreviousStorage(new HashMap<>());
		context.getPreviousSupport();
		context.setPreviousSupport(new HashMap<>());
		context.getStorageTypes();
		context.setStorageTypes(new HashMap<>());
		context.getPriceTerms();
		context.setPriceTerms(new HashMap<>());
		context.getFunctionTypes();
		context.setFunctionTypes(new HashMap<>());
		context.getPreviousFunction();
		context.setPreviousFunction(new HashMap<>());

		new AbstractUpdateContext(context) {
		}.cleanup();

	}
	/**
	 * Only there for coverage and API contracts.
	 */
	@Test
	void baseUrl() {
		final var context = newContext();
		Assertions.assertEquals("https://sample", context.getBaseUrl());
		Assertions.assertEquals("https://sample/path", context.getUrl("/path"));

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
	void copyAsNeededForce() {
		final var entity = newPrice();
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();
		newContext.setForce(true);

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
	}

	@Test
	void copyAsNeededNewNotInContext() {
		final var entity = newPrice();
		entity.setId(null);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost + repository
		final var repository = Mockito.mock(ProvInstancePriceRepository.class);
		em = Mockito.mock(EntityManager.class);
		Mockito.when(em.contains(entity)).thenReturn(false);
		copyAsNeeded(newContext, entity, consumer, repository);
		Assertions.assertEquals("-updated-", entity.getCode());
		Mockito.verify(repository, Mockito.atLeastOnce()).save(entity);
	}

	@Test
	void copyAsNeededNewInContext() {
		final var entity = newPrice();
		entity.setId(null);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost + repository
		final var repository = Mockito.mock(ProvInstancePriceRepository.class);
		em = Mockito.mock(EntityManager.class);
		Mockito.when(em.contains(entity)).thenReturn(true);
		copyAsNeeded(newContext, entity, consumer, repository);
		Assertions.assertEquals("old", entity.getCode());
		Mockito.verify(repository, Mockito.never()).save(entity);
	}

	private ProvInstancePrice newPrice() {
		final var entity = new ProvInstancePrice();
		entity.setCode("old");
		entity.setId(1);
		return entity;
	}

	@Test
	void copyAsNeededNew() {
		final var entity = newPrice();
		entity.setId(null);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("-updated-", entity.getCode());
	}

	@Test
	void copyAsNeededNo() {
		final var entity = newPrice();
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-updated-");
		final var newContext = newContext();

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("old", entity.getCode());
	}

	@Test
	void copyAsNeededTerm() {
		final var entity = new ProvInstancePriceTerm();
		entity.setCode("code");
		entity.setName("old");
		final Consumer<ProvInstancePriceTerm> consumer = t -> t.setName("-updated-");
		final var newContext = newContext();
		final var repository = Mockito.mock(ProvInstancePriceTermRepository.class);
		this.iptRepository = repository;

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer);
		Assertions.assertEquals("-updated-", entity.getName());
		newContext.getMergedTerms().contains("code");
		Mockito.verify(repository).saveAndFlush(entity);

		// No repository
		newContext.getMergedTerms().clear();
		final Consumer<ProvInstancePriceTerm> consumer2 = t -> t.setName("-renew-");
		copyAsNeeded(newContext, entity, consumer2);
		Assertions.assertEquals("-renew-", entity.getName());

		// Only one update
		final Consumer<ProvInstancePriceTerm> consumer3 = t -> t.setName("-not-called-");
		copyAsNeeded(newContext, entity, consumer3);
		Assertions.assertEquals("-renew-", entity.getName());
	}

	@Test
	void copyAsNeededType() {
		final var entity = new ProvInstanceType();
		entity.setCode("code");
		entity.setName("old");
		final Consumer<ProvInstanceType> consumer = t -> t.setName("-updated-");
		final var newContext = newContext();
		final var repository = Mockito.mock(ProvInstanceTypeRepository.class);

		// Force mode for same cost
		copyAsNeeded(newContext, entity, consumer, repository);
		Assertions.assertEquals("-updated-", entity.getName());
		newContext.getMergedTypes().contains("code");
		Mockito.verify(repository).saveAndFlush(entity);

		// No repository
		newContext.getMergedTypes().clear();
		final Consumer<ProvInstanceType> consumer2 = t -> t.setName("-renew-");
		copyAsNeeded(newContext, entity, consumer2, null);
		Assertions.assertEquals("-renew-", entity.getName());

		// Only one update
		final Consumer<ProvInstanceType> consumer3 = t -> t.setName("-not-called-");
		copyAsNeeded(newContext, entity, consumer3, repository);
		Assertions.assertEquals("-renew-", entity.getName());
	}

	@Test
	void purgeSkuEmpty() {
		final var newContext = newContext();
		final var previous = new HashMap<String, ProvInstancePrice>();
		final var pRepository = Mockito.mock(ProvInstancePriceRepository.class);
		final var qRepository = Mockito.mock(ProvQuoteInstanceRepository.class);
		purgePrices(newContext, previous, pRepository, qRepository);
		Mockito.verify(pRepository, Mockito.never()).delete(ArgumentMatchers.any());
	}

	@Test
	void purgeSku() {
		final var newContext = newContext();
		final var previous = new HashMap<String, ProvInstancePrice>();
		final var price1 = new ProvInstancePrice();
		price1.setCode("-not-updated-referenced-");
		final var price2 = new ProvInstancePrice();
		price2.setCode("-updated-");
		final var price3 = new ProvInstancePrice();
		price3.setCode("-not-updated-unused-");
		final var price4 = new ProvInstancePrice();
		price4.setCode("-another-");
		previous.put(price1.getCode(), price1);
		previous.put(price2.getCode(), price2);
		previous.put(price3.getCode(), price3);
		newContext.getPrices().add("-updated-");
		final var pRepository = Mockito.mock(ProvInstancePriceRepository.class);
		final var qRepository = Mockito.mock(ProvQuoteInstanceRepository.class);

		Mockito.doReturn(List.of(price1.getCode(), price4.getCode())).when(qRepository)
				.finUsedPrices("service:prov:some");

		purgePrices(newContext, previous, pRepository, qRepository);

		// Unused price but referenced is not deleted
		Mockito.verify(pRepository, Mockito.never()).delete(price2);

		// Unused and not updated price is deleted
		Mockito.verify(pRepository, Mockito.never()).delete(price1);

		// Unused and not updated price is deleted
		Mockito.verify(pRepository, Mockito.times(1)).delete(price3);
	}

	@Test
	void getRate() throws IOException {
		check("test-resource", Rate.BEST);
	}

	@Test
	void getRateNoDefault() throws IOException {
		check("test-resource-no-default", Rate.MEDIUM);
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
	void getWorkload() {
		Assertions.assertEquals(0, getWorkload(null));
	}

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
	void initContext() {
		nodeRepository = Mockito.mock(NodeRepository.class);
		configuration = Mockito.mock(ConfigurationResource.class);
		Mockito.when(nodeRepository.findOneExpected("service:prov:test")).thenReturn(new Node());
		Mockito.when(configuration.get(CONF_HOURS_MONTH, ProvResource.DEFAULT_HOURS_MONTH)).thenReturn(1);
		final AbstractUpdateContext context = new AbstractUpdateContext() {
			// Nothing
		};
		initContext(context, "service:prov:test", true);
		Assertions.assertNotNull(context.getNode());
		Assertions.assertEquals(1, context.getHoursMonth());
		Assertions.assertTrue(context.isForce());
	}

	@BeforeEach
	void setupEm() {
		this.em = Mockito.mock(EntityManager.class);
	}

	@Test
	void installRegion() {
		final var context = newContext();
		context.setRegions(new HashMap<>());

		final var oldRegion = new ProvLocation();
		oldRegion.setContinentM49(250);
		context.getMapRegionById().put("newRegion", oldRegion);
		locationRepository = Mockito.mock(ProvLocationRepository.class);
		final var installRegion = installRegion(context, "newRegion");
		Assertions.assertEquals("newRegion", installRegion.getName());
		Assertions.assertEquals(250, installRegion.getContinentM49().intValue());
		installRegion.setLatitude(1D);
		final var installRegion2 = installRegion(context, "newRegion");
		Assertions.assertSame(installRegion2, installRegion);
	}

	@Test
	void isEnabledDatabase() {
		final var context = newContext();
		context.setValidDatabaseType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledDatabaseType(context, "axr"));
		Assertions.assertFalse(isEnabledDatabaseType(context, null));
		Assertions.assertTrue(isEnabledDatabaseType(context, "abr"));
	}

	@Test
	void isEnabledContainer() {
		final var context = newContext();
		context.setValidContainerType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledContainerType(context, "axr"));
		Assertions.assertFalse(isEnabledContainerType(context, null));
		Assertions.assertTrue(isEnabledContainerType(context, "abr"));
	}

	@Test
	void isEnabledEngine() {
		final var context = newContext();
		context.setValidDatabaseEngine(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledEngine(context, "axr"));
		Assertions.assertTrue(isEnabledEngine(context, "abr"));
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
	void isEnabledRegion() {
		final var context = newContext();
		context.setValidRegion(Pattern.compile(".*"));
		final var location = new ProvLocation();
		location.setName("name");
		Assertions.assertTrue(isEnabledRegion(context, location));
	}

	@Test
	void isEnabledType() {
		final var context = newContext();
		context.setValidInstanceType(Pattern.compile("ab.*"));
		Assertions.assertFalse(isEnabledType(context, "axr"));
		Assertions.assertTrue(isEnabledType(context, "abr"));
	}

	private AbstractUpdateContext newContext() {
		final var node = new Node();
		node.setName("newNode");
		node.setId("service:prov:some");

		final var context = new AbstractUpdateContext() {
		};
		context.setNode(node);
		context.setBaseUrl("https://sample");
		return context;
	}

	@Test
	void nextStepIgnore() {
		nextStep(newContext(), "phase", "location", 0);
	}

	@SuppressWarnings("unchecked")
	@Test
	void nextStepLocation() {
		final var context = newContext();
		final var status = new ImportCatalogStatus();

		Mockito.doAnswer(invocation -> {
			((Consumer<ImportCatalogStatus>) invocation.getArguments()[1]).accept(status);
			return null;
		}).when(importCatalogResource).nextStep(ArgumentMatchers.any(), ArgumentMatchers.any());
		nextStep(context, "phase", "location", 1);
		Assertions.assertEquals("location", status.getLocation());
	}

	@SuppressWarnings("unchecked")
	@Test
	void nextStepPhase() {
		final var context = newContext();
		final var status = new ImportCatalogStatus();

		Mockito.doAnswer(invocation -> {
			((Consumer<ImportCatalogStatus>) invocation.getArguments()[1]).accept(status);
			return null;
		}).when(importCatalogResource).nextStep(ArgumentMatchers.any(), ArgumentMatchers.any());
		nextStep(context, "phase");
		Assertions.assertEquals("phase", status.getPhase());
	}

	@Test
	void round3Decimals() {
		Assertions.assertEquals(1.235, super.round3Decimals(1.2348), 0.001);
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
	void saveAsNeededCostGb() {
		final var entity = new ProvStoragePrice();
		entity.setId(1);
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
		entity.setId(1);
		entity.setCostGb(1d);
		@SuppressWarnings("unchecked")
		final RestRepository<ProvStoragePrice, Integer> repository = Mockito.mock(RestRepository.class);
		saveAsNeeded(newContext(), entity, 1, repository);
		Assertions.assertEquals(1, entity.getCostGb());
		Mockito.verify(repository, Mockito.never()).save(entity);
	}

	@Test
	void saveAsNeededForce() {
		final var entity = newPrice();
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
	void saveAsNeededPrice() {
		final var entity = new ProvSupportPrice();
		entity.setCost(2d);
		entity.setId(1);
		entity.setCode("code");
		final var repository = Mockito.mock(ProvSupportPriceRepository.class);
		final var context = newContext();
		saveAsNeeded(context, entity, 3, repository);
		Assertions.assertEquals(3, entity.getCost());
		context.getMergedTypes().contains("code");
		Mockito.verify(repository).save(entity);
	}

	@Test
	void saveAsNeedeNew() {
		final var entity = new ProvSupportPrice();
		entity.setCost(2d);
		entity.setCode("code");
		final var repository = Mockito.mock(ProvSupportPriceRepository.class);
		final var context = newContext();
		saveAsNeeded(context, entity, 2d, repository);
		Assertions.assertEquals(2d, entity.getCost());
		context.getMergedTypes().contains("code");
		Mockito.verify(repository).save(entity);
	}

	/**
	 * New (without identifier) but already in the entity manager.
	 */
	@Test
	void saveAsNeedeNewInEm() {
		final var entity = new ProvSupportPrice();
		entity.setCost(2d);
		entity.setCode("code");
		final var repository = Mockito.mock(ProvSupportPriceRepository.class);
		Mockito.doReturn(true).when(this.em).contains(entity);
		final var context = newContext();
		saveAsNeeded(context, entity, 2d, repository);
		Assertions.assertEquals(2d, entity.getCost());
		context.getMergedTypes().contains("code");
		Mockito.verify(repository, Mockito.never()).save(entity);
	}

	@Test
	void saveAsNeededPriceTerm() {
		final var entity = new ProvInstancePrice();
		final var term = new ProvInstancePriceTerm();
		term.setPeriod(12d);
		entity.setId(1);
		entity.setTerm(term);
		entity.setCost(2d);
		entity.setCostPeriod(24d);
		entity.setCode("code");
		final var repository = Mockito.mock(ProvInstancePriceRepository.class);
		final var context = newContext();
		saveAsNeeded(context, entity, 3, repository);
		Assertions.assertEquals(3, entity.getCost());
		Assertions.assertEquals(36, entity.getCostPeriod());
		context.getPrices().contains("code");
		Mockito.verify(repository).save(entity);
	}

	@Test
	void saveAsNeededSame() {
		final var entity = newPrice();
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-nerver-called-");
		saveAsNeeded(newContext(), entity, 1, 1, null, consumer);
		Assertions.assertEquals("old", entity.getCode());
	}

	@Test
	void saveAsNeededSameRound() {
		final var entity = newPrice();
		entity.setCost(1d);
		final Consumer<ProvInstancePrice> consumer = p -> p.setCode("-never-called-");
		saveAsNeeded(newContext(), entity, 2.012d, 2.01234d, (cRound, c) -> {
			entity.setCost(cRound);
		}, consumer);
		Assertions.assertEquals("old", entity.getCode());
		Assertions.assertEquals(1d, entity.getCost());
	}

	@Test
	void toMap() throws IOException {
		Assertions.assertEquals("WORST", super.toMap("rate-test-resource.json", MAP_STR).get("b1"));
	}

	@Test
	void toPercent() {
		Assertions.assertEquals(1.23d, super.toPercent("1.23%"), 0.001);
	}

	@Test
	void toPercentNull() {
		Assertions.assertNull(super.toPercent("some"));
	}

	@Test
	void toVmOs() {
		Assertions.assertEquals(VmOs.WINDOWS, toVmOs("windows"));
	}
}
