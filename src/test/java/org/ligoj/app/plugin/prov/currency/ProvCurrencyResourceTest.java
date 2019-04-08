/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.currency;

import java.io.IOException;

import javax.transaction.Transactional;
import javax.ws.rs.core.UriInfo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractAppTest;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.dao.ProvCurrencyRepository;
import org.ligoj.app.plugin.prov.model.CurrencyVo;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.bootstrap.core.json.TableItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvCurrencyResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvCurrencyResourceTest extends AbstractAppTest {

	@Autowired
	private ProvCurrencyResource resource;

	@Autowired
	private ProvCurrencyRepository repository;

	@BeforeEach
	public void prepare() throws IOException {
		persistEntities("csv", Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
				ProvQuote.class);
		em.flush();
		em.clear();
	}

	@Test
	public void findAll() {
		final UriInfo uriInfo = newUriInfo();
		final TableItem<CurrencyVo> result = resource.findAll(uriInfo);
		Assertions.assertEquals(1, result.getData().size());
		Assertions.assertEquals("$", result.getData().get(0).getUnit());
		Assertions.assertEquals("USD", result.getData().get(0).getName());
		Assertions.assertEquals(1, result.getData().get(0).getRate());
		Assertions.assertEquals(1, result.getData().get(0).getNbQuotes());
	}

	@Test
	public void create() {
		resource.create(newCurrency());
		Assertions.assertEquals(.8, repository.findByName("EURO").getRate());
		Assertions.assertEquals(0, resource.findAll(newUriInfo("EURO")).getData().get(0).getNbQuotes());
	}

	@Test
	public void update() {
		final ProvCurrency entity = repository.findByName("USD");
		entity.setRate(1.1);
		entity.setUnit("D");
		entity.setName("U");
		resource.update(entity);
		em.flush();
		em.clear();
		Assertions.assertEquals(1.1, repository.findByName("U").getRate());
		Assertions.assertEquals("D", repository.findByName("U").getUnit());
	}

	@Test
	public void deleteUsed() {
		Assertions.assertEquals(1, repository.count());
		Assertions.assertThrows(DataIntegrityViolationException.class, ()->resource.delete(repository.findByName("USD").getId()));
		Assertions.assertEquals(1, repository.count());
	}

	@Test
	public void delete() {
		Assertions.assertEquals(1, repository.count());
		em.createQuery("UPDATE ProvQuote SET currency = NULL").executeUpdate();
		resource.delete(repository.findByName("USD").getId());
		Assertions.assertEquals(0, repository.count());
	}

	private ProvCurrency newCurrency() {
		final ProvCurrency entity = new ProvCurrency();
		entity.setName("EURO");
		entity.setUnit("€");
		entity.setRate(.8);
		return entity;
	}

}
