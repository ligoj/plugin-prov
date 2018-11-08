/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.dao.ProvQuoteSupportRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteSupportResource}
 */
public class ProvQuoteSupportResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteSupportResource qsResource;

	@Autowired
	private ProvQuoteSupportRepository qsRepository;

	@Autowired
	private ProvSupportPriceRepository spRepository;

	@Override
	@BeforeEach
	public void prepareData() throws IOException {
		super.prepareData();
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class },
				StandardCharsets.UTF_8.name());
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
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

	@Test
	public void create() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(3);
		final UpdatedCost cost = qsResource.create(vo);
		checkCost(cost.getTotal(), 3541.94, 6236.5, false);
		checkCost(cost.getCost(), 376.54, 621.5, false);

		final QuoteVo quoteVo = resource.getConfiguration(subscription);
		checkCost(quoteVo.getCostNoSupport(), 3165.4, 5615.0, false);
		checkCost(quoteVo.getCost(), 3541.94, 6236.5, false);
		checkCost(quoteVo.getCostSupport(), 376.54, 621.5, false);
		Assertions.assertEquals(0, cost.getRelated().size());

		final int id = cost.getId();
		Assertions.assertEquals(1, quoteVo.getSupports().size());
		Assertions.assertEquals(id, quoteVo.getSupports().get(0).getId().intValue());
		Assertions.assertEquals("support1", quoteVo.getSupports().get(0).getPrice().getType().getName());

		em.flush();
		em.clear();

		// Check the exact new cost
		checkCost(subscription, 3541.94, 6236.5, false);
		final ProvQuoteSupport support = qsRepository.findOneExpected(id);
		Assertions.assertEquals("support-name1", support.getName());
		Assertions.assertEquals("support1D", support.getDescription());
		Assertions.assertEquals("support1", support.getPrice().getType().getName());
		Assertions.assertEquals(3, support.getSeats().intValue());
		Assertions.assertEquals(376.54, support.getCost(), DELTA);
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
	public void createMultipleSupport() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(10); // Too much seats for only ONE support1 -> nb = 3 (2x4 + 2 unused)
		final UpdatedCost cost = qsResource.create(vo);
		// support1 : cost=5;min=10;rates=20,15,10;limits=100,1000;seats=4
		// base cost: 3165.4 5615.0
		// support : CEIL(10/4)*(5+0.2*100+0.15*900+0,1*2165.4)
		// support : 3*(5+20+135+216.54)
		// support : 3*376.54
		// support : 1129.62
		// total : 4295.02 7479.5

		checkCost(cost.getTotal(), 4295.02, 7479.5, false);
		checkCost(cost.getCost(), 1129.62, 1864.5, false);
		Assertions.assertEquals(0, cost.getRelated().size());
	}

	@Test
	public void createInvalidRequirement() {
		final QuoteSupportEditionVo vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(null); // Unlimited seats
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
		checkCost(cost.getCost(), 376.54, 621.5, false);

		// No change
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 376.54, 621.5, false);
		Assertions.assertEquals("support1", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change some constraints
		vo.setAccessApi(SupportType.TECHNICAL);
		vo.setAccessPhone(null);
		vo.setId(cost.getId());

		// Cost is the same since the type still match the constraints
		checkCost(qsResource.update(vo).getCost(), 376.54, 621.5, false);
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 376.54, 621.5, false);

		// Change the last important constraints
		vo.setAccessApi(null);

		// Cost is the same since the type still match the constraints
		checkCost(qsResource.update(vo).getCost(), 376.54, 621.5, false);

		// The cost changed since a best type matches to the constraints
		checkCost(qsResource.refresh(qsRepository.findOneExpected(cost.getId())), 338.54, 502.75, false);
		Assertions.assertEquals("support2", qsRepository.findOneExpected(cost.getId()).getPrice().getType().getName());
	}

	@Test
	public void deleteAllSupports() {
		em.persist(newSupport("support1", 3));
		em.persist(newSupport("support2", 4));
		Assertions.assertEquals(2, qsRepository.count());
		em.flush();
		em.clear();
		// @see #delete() for details of this computation
		checkCost(resource.refresh(subscription), 3880.48, 6739.25, false);

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
		// = CEIL(3/4)*(5+0.2*100+0.15*900+0,1*2165.4)
		// = 1*(5+20+135+216.54)
		// = 376.54
		//
		// support2: 15,10,5 - 400,4000
		// = CEIL(4/10)*(2+0.15*400+0.1*2765.4)
		// = 1*(2 + 60 + 276.54)
		// = 338.54
		//
		//
		// = 3165.4 + 715.08
		// = 3880.48
		checkCost(resource.refresh(subscription), 3880.48, 6739.25, false);
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

		checkCost(qsResource.delete(id), 3503.94, 6117.75, false);
		checkCost(subscription, 3503.94, 6117.75, false);
		checkCost(resource.refresh(subscription), 3503.94, 6117.75, false);

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
		final List<QuoteSupportLookup> prices = qsResource.lookup(subscription, 3, null, null, null, null, null);

		// Lowest price first
		final QuoteSupportLookup priceS2 = prices.get(0);
		Assertions.assertEquals(338.54, priceS2.getCost(), DELTA);
		Assertions.assertEquals(3, priceS2.getSeats().intValue());
		Assertions.assertEquals("support2", priceS2.getPrice().getType().getName());

		// Check the support result
		final QuoteSupportLookup price = prices.get(1);
		assertCSP(price);
		Assertions.assertEquals(376.54, price.getCost(), DELTA);
		Assertions.assertEquals(3, price.getSeats().intValue());
		Assertions.assertTrue(price.toString().endsWith("type=AbstractNamedEntity(name=support1))), seats=3)"));
	}

	/**
	 * Lookup zero seat.
	 */
	@Test
	public void lookupNoSeat() throws IOException {
		final QuoteSupportLookup lookup = qsResource
				.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, null).get(1);
		final String asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":376.54,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":5.0,\"location\""));
		Assertions.assertTrue(asJson.contains("\"name\":\"support1\""));

		// Check the support result
		assertCSP(lookup);
		Assertions.assertEquals(376.54, lookup.getCost(), DELTA);
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
	 * Lookup unlimited seat.
	 */
	@Test
	public void lookupUnlimitedSeat() throws IOException {
		// Support1 is now unlimited seats
		spRepository.findBy("type.name", "support1").getType().setSeats(null);

		final QuoteSupportLookup lookup = qsResource
				.lookup(subscription, null, null, SupportType.TECHNICAL, null, null, null).get(0);
		final String asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":376.54,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":5.0,\"location\""));
		Assertions.assertTrue(asJson.contains("\"name\":\"support1\""));

		// Check the support result
		assertCSP(lookup);
		Assertions.assertEquals(376.54, lookup.getCost(), DELTA);
	}

	/**
	 * Filtered lookup. Only some support type are valid.
	 */
	@Test
	public void lookupFiltered() {

		// Both support1 and support2 are valid, but support2 is cheaper
		Assertions.assertEquals("support2",
				qsResource.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, null).get(0).getPrice()
						.getType().getName());

		// Only support1 provides chat access
		Assertions.assertEquals("support1",
				qsResource.lookup(subscription, 0, null, null, SupportType.TECHNICAL, null, null).get(0).getPrice()
						.getType().getName());
	}

	/**
	 * Too much requirements for an instance
	 */
	@Test
	public void lookupNoMatch() {
		Assertions.assertEquals(0, qsResource.lookup(subscription, 1, null, null, null, null, Rate.BEST).size());
		Assertions.assertEquals(0, qsResource.lookup(subscription, 1, SupportType.TECHNICAL, SupportType.TECHNICAL,
				SupportType.TECHNICAL, null, Rate.GOOD).size());
		Assertions.assertEquals(0,
				qsResource.lookup(subscription, null, null, null, SupportType.TECHNICAL, null, null).size());
	}
}
