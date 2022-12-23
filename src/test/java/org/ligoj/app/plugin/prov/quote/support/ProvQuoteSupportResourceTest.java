/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.persistence.EntityNotFoundException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.bootstrap.MatcherUtil;
import org.ligoj.bootstrap.core.json.ObjectMapperTrim;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;

/**
 * Test class of {@link ProvQuoteSupportResource}
 */
class ProvQuoteSupportResourceTest extends AbstractProvResourceTest {

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
		super.prepareData();
		persistEntities("csv", new Class[] { ProvSupportType.class, ProvSupportPrice.class },
				StandardCharsets.UTF_8.name());
		checkCost(resource.refresh(subscription), 3165.4, 5615.0, false);
	}

	@Test
	void computeRates() {
		// No rate
		Assertions.assertEquals(0, qs2Resource.computeRates(0, 0, new int[0], new int[0]));
		Assertions.assertEquals(0, qs2Resource.computeRates(1000, 0, new int[0], new int[0]));
		Assertions.assertEquals(10, qs2Resource.computeRates(1000, 10, new int[0], new int[0]));

		// 1 rate
		Assertions.assertEquals(0, qs2Resource.computeRates(0, 0, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(100,
				qs2Resource.computeRates(1000, 0, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(200,
				qs2Resource.computeRates(1000, 200, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));
		Assertions.assertEquals(1000,
				qs2Resource.computeRates(10000, 200, new int[] { 10 }, new int[] { Integer.MAX_VALUE }));

		// 2 rates
		Assertions.assertEquals(0,
				qs2Resource.computeRates(0, 0, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(55,
				qs2Resource.computeRates(1000, 0, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(200,
				qs2Resource.computeRates(1000, 200, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));
		Assertions.assertEquals(505,
				qs2Resource.computeRates(10000, 200, new int[] { 10, 5 }, new int[] { 100, Integer.MAX_VALUE }));

		// 2 implicit max range
		Assertions.assertEquals(0, qs2Resource.computeRates(0, 0, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(55, qs2Resource.computeRates(1000, 0, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(200, qs2Resource.computeRates(1000, 200, new int[] { 10, 5 }, new int[] { 100 }));
		Assertions.assertEquals(505, qs2Resource.computeRates(10000, 200, new int[] { 10, 5 }, new int[] { 100 }));

		// AWS Developer
		Assertions.assertEquals(60, qs2Resource.computeRates(2000, 29, new int[] { 3 }, new int[] {}));

		// AWS Business
		Assertions.assertEquals(6150,
				qs2Resource.computeRates(85000, 100, new int[] { 10, 7, 5, 3 }, new int[] { 10000, 80000, 250000 }));

		// AWS Enterprise
		Assertions.assertEquals(70500, qs2Resource.computeRates(1200000, 15000, new int[] { 10, 7, 5, 3 },
				new int[] { 150000, 500000, 1000000 }));
	}

	@Test
	void create() {
		final var vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(3);
		vo.setAccessChat(null);
		vo.setAccessEmail(null);
		vo.setLevel(Rate.LOW);
		newTags(vo);

		final var cost = qs2Resource.create(vo);
		checkCost(cost.getTotal(), 3541.94, 6236.5, false);
		checkCost(cost.getCost(), 376.54, 621.5, false);

		final var quoteVo = resource.getConfiguration(subscription);
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
		final var support = qs2Repository.findOneExpected(id);
		Assertions.assertEquals("support-name1", support.getName());
		Assertions.assertEquals("support1D", support.getDescription());
		Assertions.assertEquals("support1", support.getPrice().getType().getName());
		Assertions.assertEquals(ResourceType.SUPPORT, support.getResourceType());
		Assertions.assertEquals(3, support.getSeats().intValue());
		Assertions.assertNull(support.getSlaStartTime());
		Assertions.assertNull(support.getSlaEndTime());
		Assertions.assertFalse(support.isSlaWeekEnd());
		Assertions.assertEquals(376.54, support.getCost(), DELTA);

		Assertions.assertTrue(support.getPrice().getType().isSlaWeekEnd());

		Assertions.assertEquals(1, support.getPrice().getType().getSlaStartTime().intValue());
		Assertions.assertEquals(2, support.getPrice().getType().getSlaEndTime().intValue());
		Assertions.assertEquals(3, support.getPrice().getType().getCommitment());
		Assertions.assertEquals(4, support.getPrice().getType().getSeats().intValue());

		Assertions.assertEquals(5, support.getPrice().getType().getSlaGeneralGuidance().intValue());
		Assertions.assertEquals(6, support.getPrice().getType().getSlaSystemImpaired().intValue());
		Assertions.assertEquals(7, support.getPrice().getType().getSlaProductionSystemImpaired().intValue());
		Assertions.assertEquals(8, support.getPrice().getType().getSlaProductionSystemDown().intValue());
		Assertions.assertEquals(9, support.getPrice().getType().getSlaBusinessCriticalSystemDown().intValue());

		Assertions.assertFalse(support.isUnboundCost());

		assertTags(support);

		// Coverage only
		Assertions.assertEquals("service:prov:test", support.getPrice().getType().getNode().getId());
	}

	@Test
	void createInvalidType() {
		final var vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setType("not-exist");
		Assertions.assertThrows(EntityNotFoundException.class, () -> qs2Resource.create(vo));
	}

	@Test
	void createMultipleSupport() {
		final var vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(10); // Too much seats for only ONE support1 -> nb = 3 (2x4 + 2 unused)
		final var cost = qs2Resource.create(vo);
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
	void createInvalidRequirement() {
		final var vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("support-name1");
		vo.setDescription("support1D");
		vo.setType("support1");
		vo.setSeats(null); // Unlimited seats
		MatcherUtil.assertThrows(Assertions.assertThrows(ValidationJsonException.class, () -> qs2Resource.create(vo)),
				"type", "type-incompatible-requirements");
	}

	@Test
	void refresh() {
		// Create with constraints
		final var vo = new QuoteSupportEditionVo();
		vo.setSubscription(subscription);
		vo.setName("server-new");
		vo.setType("support1");
		vo.setSeats(3);
		vo.setAccessApi(SupportType.ALL);
		vo.setAccessPhone(SupportType.TECHNICAL);
		final var cost = qs2Resource.create(vo);
		checkCost(cost.getCost(), 376.54, 621.5, false);

		// No change
		checkCost(qs2Resource.refresh(qs2Repository.findOneExpected(cost.getId())), 376.54, 621.5, false);
		Assertions.assertEquals("support1", qs2Repository.findOneExpected(cost.getId()).getPrice().getType().getName());

		// Change some constraints
		vo.setAccessApi(SupportType.TECHNICAL);
		vo.setAccessPhone(null);
		vo.setId(cost.getId());

		// Cost is the same since the type still match the constraints
		checkCost(qs2Resource.update(vo).getCost(), 376.54, 621.5, false);
		checkCost(qs2Resource.refresh(qs2Repository.findOneExpected(cost.getId())), 376.54, 621.5, false);

		// Change the last important constraints
		vo.setAccessApi(null);

		// Cost is the same since the type still match the constraints
		checkCost(qs2Resource.update(vo).getCost(), 376.54, 621.5, false);

		// The cost changed since a better type matches to the constraints
		checkCost(qs2Resource.refresh(qs2Repository.findOneExpected(cost.getId())), 338.54, 502.75, false);
		Assertions.assertEquals("support2", qs2Repository.findOneExpected(cost.getId()).getPrice().getType().getName());
	}

	private ProvQuoteSupport newSupports() {
		final var support1 = newSupport("support1", 3);
		support1.setAccessPhone(SupportType.ALL);
		em.persist(support1);
		final var support2 = newSupport("support2", 4);
		support1.setSlaWeekEnd(false);
		support2.setLevel(Rate.GOOD);
		em.persist(support2);
		Assertions.assertEquals(2, qs2Repository.count());
		em.flush();
		em.clear();

		checkCost(resource.refresh(subscription), 3880.48, 6739.25, false);
		return support1;
	}

	@Test
	void deleteAllSupports() {
		final var support1 = newSupports();
		var deleteAll = qs2Resource.deleteAll(subscription);
		checkCost(deleteAll, 3165.4, 5615.0, false);

		// Check deleted resources response
		final var deleted = deleteAll.getDeleted().get(ResourceType.SUPPORT);
		Assertions.assertEquals(2, deleted.size());
		Assertions.assertTrue(deleted.contains(support1.getId()));

		// Check the exact new cost
		// @see #delete() for details of this computation
		checkCost(subscription, 3165.4, 5615.0, false);

		// Check the associations
		Assertions.assertEquals(0, qs2Repository.count());
	}

	@Test
	void delete() {
		newSupports();
		final var id = qs2Repository.findByNameExpected("support1").getId();
		final var configuration = resource.getConfiguration(subscription);

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
		checkCost(configuration.getCostNoSupport(), 3165.4, 5615.0, false);
		checkCost(configuration.getCostSupport(), 715.08, 1124.25, false);
		checkCost(configuration.getCost(), 3880.48, 6739.25, false);
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

		checkCost(qs2Resource.delete(id), 3503.94, 6117.75, false);
		checkCost(subscription, 3503.94, 6117.75, false);
		checkCost(resource.refresh(subscription), 3503.94, 6117.75, false);

		// Check the associations
		Assertions.assertNull(qs2Repository.findOne(id));
		Assertions.assertEquals(1, qs2Repository.count());
	}

	private ProvQuoteSupport newSupport(final String name, final int seats) {
		final var result = new ProvQuoteSupport();
		result.setName(name);
		result.setSeats(seats);
		result.setPrice(sp2Repository.findBy("type.name", name));
		result.setConfiguration(resource.getQuoteFromSubscription(subscription));
		result.setCost(0);
		result.setMaxCost(0);
		result.setSlaWeekEnd(true);
		result.setSlaStartTime(10L);
		result.setSlaEndTime(11L);
		return result;
	}

	@Test
	void findSupportType() {
		final var tableItem = qs2Resource.findType(subscription, newUriInfo());
		Assertions.assertEquals(3, tableItem.getRecordsTotal());
		Assertions.assertEquals("support1", tableItem.getData().get(0).getName());
	}

	@Test
	void findSupportTypeCriteria() {
		final var tableItem = qs2Resource.findType(subscription, newUriInfo("rt2"));
		Assertions.assertEquals(1, tableItem.getRecordsTotal());
		Assertions.assertEquals("support2", tableItem.getData().get(0).getName());
	}

	@Test
	void findSupportTypeNotExistsSubscription() {
		final var uri = newUriInfo();
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> qs2Resource.findType(-1, uri));
	}

	@Test
	void findSupportTypeAnotherSubscription() {
		Assertions.assertEquals(1,
				qs2Resource.findType(getSubscription("mda", "service:prov:x"), newUriInfo()).getData().size());
	}

	@Test
	void findSupportTypeNotVisibleSubscription() {
		initSpringSecurityContext("any");
		final var uri = newUriInfo();
		Assertions.assertThrows(EntityNotFoundException.class, () -> qs2Resource.findType(subscription, uri));
	}

	/**
	 * Basic case, almost no requirements.
	 */
	@Test
	void lookup() {
		final var prices = qs2Resource.lookup(subscription, 3, null, null, null, null, null);

		// Lowest price first
		final var priceS2 = prices.get(0);
		Assertions.assertEquals(338.54, priceS2.getCost(), DELTA);
		Assertions.assertEquals(3, priceS2.getSeats().intValue());
		Assertions.assertEquals("support2", priceS2.getPrice().getType().getName());

		// Check the support result
		final var price = prices.get(1);
		assertCSP(price);
		Assertions.assertEquals(376.54, price.getCost(), DELTA);
		Assertions.assertEquals(3, price.getSeats().intValue());
		Assertions.assertTrue(price.toString().endsWith("type=AbstractNamedEntity(name=support1))), seats=3)"));
	}

	/**
	 * Lookup zero seat.
	 */
	@Test
	void lookupNoSeat() throws IOException {
		final var lookup = qs2Resource.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, null).get(1);
		final var asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":376.54,\"co2\":0.0,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":5.0,"));
		Assertions.assertTrue(asJson.contains("\"name\":\"support1\""));

		// Check the support result
		assertCSP(lookup);
		Assertions.assertEquals(376.54, lookup.getCost(), DELTA);
	}

	private QuoteSupportLookup assertCSP(final QuoteSupportLookup price) {
		final var sp = price.getPrice();
		final var st = sp.getType();
		Assertions.assertNotNull(sp.getId());
		Assertions.assertNotNull(st.getId());
		Assertions.assertEquals("support1", st.getName());
		return price;
	}

	/**
	 * Lookup unlimited seat.
	 */
	@Test
	void lookupUnlimitedSeat() throws IOException {
		// Support1 is now unlimited seats
		sp2Repository.findBy("type.name", "support1").getType().setSeats(null);

		final var lookup = qs2Resource.lookup(subscription, null, null, SupportType.TECHNICAL, null, null, null).get(0);
		final var asJson = new ObjectMapperTrim().writeValueAsString(lookup);
		Assertions.assertTrue(asJson.startsWith("{\"cost\":376.54,\"co2\":0.0,\"price\":{\"id\":"));
		Assertions.assertTrue(asJson.contains("\"cost\":5.0,"));
		Assertions.assertTrue(asJson.contains("\"name\":\"support1\""));

		// Check the support result
		assertCSP(lookup);
		Assertions.assertEquals(376.54, lookup.getCost(), DELTA);
	}

	/**
	 * Filtered lookup. Only some support type are valid.
	 */
	@Test
	void lookupFiltered() {

		// Both support1 and support2 are valid, but support2 is cheaper
		Assertions.assertEquals("support2",
				qs2Resource.lookup(subscription, 0, null, SupportType.TECHNICAL, null, null, null).get(0).getPrice()
						.getType().getName());

		// Only support1 provides chat access
		Assertions.assertEquals("support1",
				qs2Resource.lookup(subscription, 0, null, null, SupportType.TECHNICAL, null, null).get(0).getPrice()
						.getType().getName());
	}

	/**
	 * Too many requirements for an instance
	 */
	@Test
	void lookupNoMatch() {
		Assertions.assertEquals(0, qs2Resource.lookup(subscription, 1, null, null, null, null, Rate.BEST).size());
		Assertions.assertEquals(0, qs2Resource.lookup(subscription, 1, SupportType.TECHNICAL, SupportType.TECHNICAL,
				SupportType.TECHNICAL, null, Rate.GOOD).size());
		Assertions.assertEquals(0,
				qs2Resource.lookup(subscription, null, null, null, SupportType.TECHNICAL, null, null).size());
	}

	@Test
	void filterRate() {
		Assertions.assertTrue(qs2Resource.filter((Rate) null, null));
		Assertions.assertTrue(qs2Resource.filter(Rate.GOOD, Rate.GOOD));
		Assertions.assertTrue(qs2Resource.filter(Rate.GOOD, Rate.BEST));
		Assertions.assertTrue(qs2Resource.filter(null, Rate.LOW));
		Assertions.assertFalse(qs2Resource.filter(Rate.GOOD, null));
		Assertions.assertFalse(qs2Resource.filter(Rate.GOOD, Rate.LOW));
	}

	@Test
	void filterAccess() {
		Assertions.assertTrue(qs2Resource.filter((SupportType) null, null));
		Assertions.assertTrue(qs2Resource.filter(SupportType.BILLING, SupportType.ALL));
		Assertions.assertTrue(qs2Resource.filter(SupportType.ALL, SupportType.ALL));
		Assertions.assertTrue(qs2Resource.filter(SupportType.BILLING, SupportType.BILLING));
		Assertions.assertTrue(qs2Resource.filter(null, SupportType.ALL));
		Assertions.assertFalse(qs2Resource.filter(SupportType.BILLING, null));
	}
}
