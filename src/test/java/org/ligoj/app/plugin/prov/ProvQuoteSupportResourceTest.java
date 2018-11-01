/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.persistence.EntityNotFoundException;
import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSupportRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvQuoteSupportResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvQuoteSupportResourceTest extends AbstractAppTest {

	private static final double DELTA = 0.01d;

	@Autowired
	private ProvQuoteSupportResource qsResource;

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteSupportRepository qsRepository;

	@Autowired
	private ProvSupportPriceRepository spRepository;

	private int subscription;

	@BeforeEach
	public void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvQuote.class,
						ProvSupportType.class, ProvStorageType.class, ProvSupportPrice.class, ProvStoragePrice.class,
						ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
		updateCost();
	}

	private void updateCost() {

		// Check the cost fully updated and exact actual cost
		final FloatingCost cost = resource.updateCost(subscription);
		Assertions.assertEquals(3165.4, cost.getMin(), DELTA);
		Assertions.assertEquals(5615.0, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		em.flush();
		em.clear();
	}

	@Test
	public void computeRates() {
		// No rate
		Assertions.assertEquals(0, qsResource.computeRates(0, 0, new int[0], new int[0]));
		Assertions.assertEquals(0, qsResource.computeRates(1000, 0, new int[0], new int[0]));
		Assertions.assertEquals(10, qsResource.computeRates(1000, 10, new int[0], new int[0]));

		// 1 rate
		Assertions.assertEquals(0, qsResource.computeRates(0, 0, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(100,
				qsResource.computeRates(1000, 0, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(200,
				qsResource.computeRates(1000, 200, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(1000,
				qsResource.computeRates(10000, 200, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));

		// 2 rates
		Assertions.assertEquals(0,
				qsResource.computeRates(0, 0, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(55,
				qsResource.computeRates(1000, 0, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(200,
				qsResource.computeRates(1000, 200, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(505,
				qsResource.computeRates(10000, 200, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));

		// 2 implicit max range
		Assertions.assertEquals(0, qsResource.computeRates(0, 0, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(55, qsResource.computeRates(1000, 0, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(200, qsResource.computeRates(1000, 200, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(505, qsResource.computeRates(10000, 200, new int[] { 10, 5 }, new int[] { 100 }));

		// AWS Developer
		Assertions.assertEquals(60, qsResource.computeRates(2000, 29, new int[] { 3 }, new int[] {}));

		// AWS Business
		Assertions.assertEquals(6150,
				qsResource.computeRates(85000, 100, new int[] { 10, 7, 5, 3 }, new int[] { 10000, 80000, 250000 }));

		// AWS Enterprise
		Assertions.assertEquals(70500, qsResource.computeRates(1200000, 15000, new int[] { 10, 7, 5, 3 },
				new int[] { 150000, 500000, 1000000 }));
	}

	private QuoteLigthVo checkCost(final int subscription, final double min, final double max, final boolean unbound) {
		final QuoteLigthVo status = resource.getSusbcriptionStatus(subscription);
		checkCost(status.getCost(), min, max, unbound);
		return status;
	}

	private void checkCost(final FloatingCost cost, final double min, final double max, final boolean unbound) {
		Assertions.assertEquals(min, cost.getMin(), DELTA);
		Assertions.assertEquals(max, cost.getMax(), DELTA);
		Assertions.assertEquals(unbound, cost.isUnbound());
	}

	@Test
	public void create() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(3);
		resource.refresh(subscription);resource.getConfiguration(subscription);
		final UpdatedCost cost = qsResource.create(vo);
		checkCost(cost.getTotalCost(), 3551.94, 6246.5, false);
		checkCost(cost.getResourceCost(), 386.54, 631.5, false);
		Assertions.assertEquals(0, cost.getRelatedCosts().size());
		final int id = cost.getId();
		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 3551.94, 6246.5, false);
		final ProvQuoteSupport support = qsRepository.findOneExpected(id);
		Assertions.assertEquals("support-name1", support.getName());
		Assertions.assertEquals("support1D", support.getDescription());
		Assertions.assertEquals("support1", support.getPrice().getType().getName());
		Assertions.assertEquals(3, support.getSeats().intValue());
		Assertions.assertEquals(386.54, support.getCost(), DELTA);
		Assertions.assertFalse(support.isUnboundCost());
	}

	@Test
	public void createInvalidType() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setType("not-exist");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qsResource.create(vo));
	}

	@Test
	public void createInvalidRequirement() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(10); // Too much seats for support1 (max 4)
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qsResource.create(vo)),
				"type", "type-incompatible-requirements");
	}

	@Test
	public void refresh() {
		// Create with constraints
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server-new");
		vo.setType("support1");
		vo.setSeats(3);
		vo.setAccessApi(SupportType.ALL);
		vo.setAccessPhone(SupportType.TECHNICAL);
		final UpdatedCost cost = qsResource.create(vo);
		checkCost(cost.getResourceCost(), 386.54, 631.5, false);

		// No change
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 386.54, 631.5, false);
		Assertions.assertEquals("support1", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change some constraints
		vo.setAccessApi(SupportType.TECHNICAL);
		vo.setAccessPhone(null);
		vo.setId(cost.getId());

		// Cost is the same since the type still match the constraints
		checkCost(qsResource.update(vo).getResourceCost(), 386.54, 631.5, false);
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 386.54, 631.5, false);

		// Change the last important constraints
		vo.setAccessApi(null);

		// Cost is the same since the type still match the constraints
		checkCost(qsResource.update(vo).getResourceCost(), 386.54, 631.5, false);

		// The cost changed since a best type matches to the constraints
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 386.54, 631.5, false);
		Assertions.assertEquals("support1", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());
	}

	@Test
	public void deleteAllSupports() {
		em.persist(newSupport("support1", 3));
		em.persist(newSupport("support2", 4));
		Assertions.assertEquals(2, qsRepository.count());
		em.flush();
		em.clear();

		// support1: 20,15,10 - 100,1000
		// = 3*5 + 0.2*100 + 0.15*900 + (3165,4-1000)*0,1
		// = 15 + 20 + 135 + 216,54
		// = 386,54
		//
		// support2: 15,10,5 - 400,4000
		// = 4*2 + 0.15*400 + 0.1*2765.4
		// = 8 + 60 + 276.54
		// = 344,54
		//
		//
		// = 3165,4 + 731,08
		// = 3896,48
		checkCost(resource.refresh(subscription), 3896.48, 6755.25, false);

		checkCost(qsResource.deleteAll(subscription), 3165.4, 5615.0, false);

		// Check the exact new cost
		checkCost(subscription, 3165.4, 5615.0, false);

		// Check the associations
		Assertions.assertEquals(0, qsRepository.count());
	}

	@Test
	public void delete() {
		em.persist(newSupport("support1", 3));
		em.persist(newSupport("support2", 4));
		final Integer id = qsRepository.findByNameExpected("support1").getId();
		Assertions.assertEquals(2, qsRepository.count());
		em.flush();
		em.clear();

		// support1: 20,15,10 - 100,1000
		// = 3*5 + 0.2*100 + 0.15*900 + (3165,4-1000)*0,1
		// = 15 + 20 + 135 + 216,54
		// = 386,54
		//
		// support2: 15,10,5 - 400,4000
		// = 4*2 + 0.15*400 + 0.1*2765.4
		// = 8 + 60 + 276.54
		// = 344,54
		//
		//
		// = 3165,4 + 731,08
		// = 3896,48
		checkCost(resource.refresh(subscription), 3896.48, 6755.25, false);
		em.flush();
		em.clear();

		// Check cost without refresh
		// support2: 15,10,5 - 400,4000
		// = 4*2 + 0.15*400 + 0.1*2765.4
		// = 8 + 60 + 276.54
		// = 344,54
		//
		//
		// = 3165,4 + 344,54
		// = 3509,94

		checkCost(qsResource.delete(id), 3509.94, 6123.75, false);
		checkCost(subscription, 3509.94, 6123.75, false);
		checkCost(resource.refresh(subscription), 3509.94, 6123.75, false);

		// Check the associations
		Assertions.assertNull(qsRepository.findOne(id));
		Assertions.assertEquals(1, qsRepository.count());
	}

	private ProvQuoteSupport newSupport(final String name, final int seats) {
		final ProvQuoteSupport result = new ProvQuoteSupport();
		result.setName(name);
		result.setSeats(seats);
		result.setPrice(spRepository.findBy("type.name", name));
		result.setConfiguration(resource.getQuoteFromSubscription(subscription));
		result.setCost(0);
		result.setMaxCost(0);
		return result;
	}

	@Test
	public void findSupportType() {
		final TableItem<ProvSupportType> tableItem = qsResource.findType(subscription, newUriInfo());
		Assertions.assertEquals(2, tableItem.getRecordsTotal());
		Assertions.assertEquals("support1", tableItem.getData().get(0).getName());
	}

	@Test
	public void findSupportTypeCriteria() {
		final TableItem<ProvSupportType> tableItem = qsResource.findType(subscription, newUriInfo("rt2"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("support2", tableItem.getData().get(0).getName());
	}

	@Test
	public void findSupportTypeNotExistsSubscription() {
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> {
			qsResource.findType(-1, newUriInfo());
		});
	}

	@Test
	public void findSupportTypeAnotherSubscription() {
		Assertions.assertEquals(1,
				qsResource.findType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	public void findSupportTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		Assertions.assertThrows(EntityNotFoundException.class, () -> {
			qsResource.findType(subscription, newUriInfo());
		});
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	public void lookup() {
		final QuoteSupportLookup price = qsResource.lookup(subscription, 3, null, null, null, null, false, false, false)
				.get(0);

		// Check the support result
		assertCSP(price);
		Assertions.assertEquals(386.54, price.getCost(), DELTA);
		Assertions.assertEquals(3, price.getSeats().intValue());
		Assertions.assertTrue(price.toString().endsWith("type=AbstractNamedEntity(name=support1))), seats=3)"));
	}

	/**
	 * Advanced case, all requirements.
	 */
	@Test
	public void lookupHighContraints() throws IOException {
		final QuoteSupportLookup lookup = qsResource
				.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, false, false, false).get(0);
		final String asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":371.54,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":5.0,\"location\""));
		Assertions.assertTrue(asJson.contains("\"name\":\"support1\""));

		// Check the support result
		assertCSP(lookup);
		Assertions.assertEquals(371.54, lookup.getCost(), DELTA);
	}

	private QuoteSupportLookup assertCSP(final QuoteSupportLookup price) {
		final ProvSupportPrice sp = price.getPrice();
		final ProvSupportType st = sp.getType();
		Assertions.assertNotNull(sp.getId());
		Assertions.assertNotNull(st.getId());
		Assertions.assertEquals("support1", st.getName());
		return price;
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupNoMatch() {
		Assertions.assertEquals("support2",
				qsResource.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, false, false, false).get(1)
						.getPrice().getType().getName());
		Assertions.assertEquals("support1",
				qsResource.lookup(subscription, 0, null, null, SupportType.TECHNICAL, null, false, false, false).get(0)
						.getPrice().getType().getName());
		Assertions.assertEquals("support2",
				qsResource.lookup(subscription, 0, null, null, null, null, false, false, false).get(1).getPrice()
						.getType().getName());

		// Out of limits of seats
		Assertions.assertEquals(0, qsResource
				.lookup(subscription, 1, SupportType.ALL, null, SupportType.TECHNICAL, null, true, true, true).size());
		Assertions.assertEquals(0, qsResource
				.lookup(subscription, null, null, null, SupportType.TECHNICAL, null, false, false, false).size());
	}

}
