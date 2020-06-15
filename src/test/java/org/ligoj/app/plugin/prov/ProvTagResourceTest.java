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
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
import org.ligoj.app.plugin.prov.dao.ProvTagRepository;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvTagResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class ProvTagResourceTest extends AbstractAppTest {

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvTagResource tagResource;

	private int subscription;

	@Autowired
	private ProvTagRepository tagRepository;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteStorageRepository qsRepository;

	@Autowired
	private ConfigurationResource configuration;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvStorageType.class, ProvStoragePrice.class,
						ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		clearAllCache();
		resource.refresh(subscription);
	}

	@Test
	void update() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("server1").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		vo.setId(tagResource.create(subscription, vo));
		final var storage = qsRepository.findByName("server1-root").getId();
		vo.setResource(storage);
		vo.setType(ResourceType.STORAGE);
		vo.setName("key2");
		vo.setValue("value2");
		tagResource.update(subscription, vo);

		final var entity = tagRepository.findOne(vo.getId());
		Assertions.assertEquals("key2", entity.getName());
		Assertions.assertEquals("value2", entity.getValue());
		Assertions.assertEquals(vo.getId(), entity.getId().intValue());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals(storage, entity.getResource().intValue());
		Assertions.assertEquals(ResourceType.STORAGE, entity.getType());
	}

	@Test
	void updateAnotherSubscription() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("server1").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		vo.setId(tagResource.create(subscription, vo));
		vo.setResource(qiRepository.findByName("serverX").getId());
		Assertions.assertThrows(EntityNotFoundException.class, () -> tagResource.update(subscription, vo));
	}

	@Test
	void create() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("server1").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		final var id = tagResource.create(subscription, vo);

		final var vo2 = new TagEditionVo();
		vo2.setName("key");
		vo2.setValue("value2");
		vo2.setResource(instance);
		vo2.setType(ResourceType.INSTANCE);
		final var id2 = tagResource.create(subscription, vo2);

		final var entity = tagRepository.findOne(id);
		Assertions.assertEquals("key", entity.getName());
		Assertions.assertEquals(id, entity.getId().intValue());
		Assertions.assertEquals(subscription, entity.getConfiguration().getSubscription().getId().intValue());
		Assertions.assertEquals("value", entity.getValue());
		Assertions.assertEquals(instance, entity.getResource().intValue());
		Assertions.assertEquals(ResourceType.INSTANCE, entity.getType());

		// Check the tag from the configuration view
		final var tag = resource.getConfiguration(subscription).getTags().get(ResourceType.INSTANCE).get(instance)
				.get(0);
		Assertions.assertEquals("key", tag.getName());
		Assertions.assertEquals("value", tag.getValue());
		Assertions.assertEquals(id, tag.getId());

		// Check the tag from the configuration view 2
		final var tag2 = resource.getConfiguration(subscription).getTags().get(ResourceType.INSTANCE).get(instance)
				.get(1);
		Assertions.assertEquals("key", tag2.getName());
		Assertions.assertEquals("value2", tag2.getValue());
		Assertions.assertEquals(id2, tag2.getId());
	}

	@Test
	void createNotExistingSubscription() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("server1").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		Assertions.assertThrows(EntityNotFoundException.class, () -> tagResource.create(0, vo));
	}

	@Test
	void createNotExistingResource() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		vo.setResource(0);
		vo.setType(ResourceType.INSTANCE);
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> tagResource.create(subscription, vo));
	}

	@Test
	void createAnotherSubscription() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("serverX").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		Assertions.assertThrows(EntityNotFoundException.class, () -> tagResource.create(subscription, vo));
	}

	@Test
	void deleteAnotherSubscription() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		vo.setResource(qiRepository.findByName("serverX").getId());
		vo.setType(ResourceType.INSTANCE);

		Assertions.assertThrows(EntityNotFoundException.class, () -> tagResource.create(subscription, vo));
	}

	@Test
	void delete() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		vo.setResource(qiRepository.findByName("server1").getId());
		vo.setType(ResourceType.INSTANCE);
		final var id = tagResource.create(subscription, vo);
		vo.setResource(qsRepository.findByName("server1-root").getId());
		vo.setType(ResourceType.STORAGE);
		vo.setName("key2");
		vo.setValue("value2");
		final var id2 = tagResource.create(subscription, vo);

		tagResource.delete(subscription, id);
		Assertions.assertFalse(tagRepository.existsById(id));
		Assertions.assertTrue(tagRepository.existsById(id2));
	}

	@Test
	void deleteFromResource() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		final var instance = qiRepository.findByName("server1").getId();
		vo.setResource(instance);
		vo.setType(ResourceType.INSTANCE);
		final var id = tagResource.create(subscription, vo);
		vo.setResource(qsRepository.findByName("server1-root").getId());
		vo.setType(ResourceType.STORAGE);
		vo.setName("key2");
		vo.setValue("value2");
		final var id2 = tagResource.create(subscription, vo);

		qiResource.delete(instance);

		Assertions.assertFalse(tagRepository.existsById(id));
		Assertions.assertFalse(tagRepository.existsById(id2));
	}

	@Test
	void deleteAllFromResource() {
		final var vo = new TagEditionVo();
		vo.setName("key");
		vo.setValue("value");
		vo.setResource(qiRepository.findByName("server1").getId());
		vo.setType(ResourceType.INSTANCE);
		final var id = tagResource.create(subscription, vo);
		vo.setResource(qsRepository.findByName("server1-root").getId());
		vo.setType(ResourceType.STORAGE);
		vo.setName("key2");
		vo.setValue("value2");
		final var id2 = tagResource.create(subscription, vo);

		qsResource.deleteAll(subscription);

		Assertions.assertTrue(tagRepository.existsById(id));
		Assertions.assertFalse(tagRepository.existsById(id2));

		qiResource.deleteAll(subscription);
		Assertions.assertFalse(tagRepository.existsById(id));
	}

}
