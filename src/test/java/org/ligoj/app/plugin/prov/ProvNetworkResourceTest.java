/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import org.ligoj.app.plugin.prov.dao.ProvNetworkRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteStorageRepository;
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
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaObjectRetrievalFailureException;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvNetworkResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
public class ProvNetworkResourceTest extends AbstractAppTest {

	@Autowired
	private ProvResource resource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteStorageResource qsResource;

	@Autowired
	private ProvQuoteDatabaseResource qbResource;

	@Autowired
	private ProvNetworkResource networkResource;

	private int subscription;

	@Autowired
	private ProvNetworkRepository networkRepository;

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
						ProvQuote.class, ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class,
						ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class,
						ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		resource.refresh(subscription);
	}

	private NetworkVo newVo(final boolean inbound, final int peer, final ResourceType type) {
		final var vo = new NetworkVo();
		vo.setInbound(inbound);
		vo.setName("key");
		vo.setPeer(peer);
		vo.setPeerType(type);
		vo.setPort(1);
		vo.setRate(2);
		vo.setThroughput(3);
		return vo;
	}

	private List<NetworkVo> prepare() {
		final List<NetworkVo> io = new ArrayList<>();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		io.add(newVo(true, server2, ResourceType.INSTANCE));
		io.add(newVo(false, storage1, ResourceType.STORAGE));
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		return io;
	}

	@Test
	void update() {
		final List<NetworkVo> io = prepare();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var server3 = qiRepository.findByName("server3").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		final var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(2, list.size());
		Assertions.assertEquals(server2, list.get(0).getSource());
		Assertions.assertEquals(ResourceType.INSTANCE, list.get(0).getSourceType());
		Assertions.assertEquals(1, list.get(0).getPort());
		Assertions.assertEquals(2, list.get(0).getRate());
		Assertions.assertEquals(3, list.get(0).getThroughput());
		Assertions.assertEquals(server1, list.get(0).getTarget());
		Assertions.assertEquals(ResourceType.INSTANCE, list.get(0).getTargetType());

		Assertions.assertEquals(server1, list.get(1).getSource());
		Assertions.assertEquals(ResourceType.INSTANCE, list.get(1).getSourceType());
		Assertions.assertEquals("key", list.get(1).getName());
		Assertions.assertEquals(1, list.get(1).getPort());
		Assertions.assertEquals(2, list.get(1).getRate());
		Assertions.assertEquals(3, list.get(1).getThroughput());
		Assertions.assertEquals(storage1, list.get(1).getTarget());
		Assertions.assertEquals(ResourceType.STORAGE, list.get(1).getTargetType());
		final var networks = resource.getConfiguration(subscription).getNetworks();
		Assertions.assertEquals(2, networks.size());
		Assertions.assertEquals(1, networks.get(0).getPort());
		Assertions.assertEquals("key", list.get(0).getName());
		Assertions.assertNotNull(list.get(0).getConfiguration());

		// Replace the IO links to only one link server1->server3
		io.clear();
		io.add(newVo(false, server3, ResourceType.INSTANCE));
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		final var list2 = networkRepository.findAll(subscription);
		Assertions.assertEquals(1, list2.size());
		Assertions.assertEquals(server1, list2.get(0).getSource());
		Assertions.assertEquals(server3, list2.get(0).getTarget());
		Assertions.assertEquals(1, resource.getConfiguration(subscription).getNetworks().size());

		// Remove all IO
		io.clear();
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		Assertions.assertEquals(0, networkRepository.findAll(subscription).size());
		Assertions.assertEquals(0, networks.size());
	}

	@Test
	void deleteRelatedStorage() {
		prepare();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		// Check the cascaded delete of a related resource
		qsResource.delete(storage1);
		Assertions.assertEquals(1, networkRepository.findAll(subscription).size());

	}

	@Test
	void deleteRelatedInstance() {
		prepare();
		final var server1 = qiRepository.findByName("server1").getId();

		// Check the cascaded delete of a related resource (two ways)
		qiResource.delete(server1);
		Assertions.assertEquals(0, networkRepository.findAll(subscription).size());
	}

	@Test
	void deleteAllInstance() {
		prepare();

		// Check the cascaded deleteAll of a related resource type
		qiResource.deleteAll(subscription);
		Assertions.assertEquals(0, networkRepository.findAll(subscription).size());
	}

	@Test
	void deleteAllStorage() {
		prepare();
		qsResource.deleteAll(subscription);
		Assertions.assertEquals(1, networkRepository.findAll(subscription).size());
	}

	@Test
	void deleteAllUnrelated() {
		prepare();
		qbResource.deleteAll(subscription);
		Assertions.assertEquals(2, networkRepository.findAll(subscription).size());
	}

	@Test
	void updateAnotherSubscription1() {
		updateAnotherSubscription("serverX", "server1");
	}

	@Test
	void updateAnotherSubscription2() {
		updateAnotherSubscription("server1", "serverX");
	}

	private void updateAnotherSubscription(final String s1Name, final String s2Name) {
		final var s1 = qiRepository.findByName(s1Name).getId();
		final var s2 = qiRepository.findByName(s2Name).getId();

		final var vo = new NetworkVo();
		vo.setPeer(s2);
		vo.setPeerType(ResourceType.INSTANCE);
		vo.setPort(1);

		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.update(subscription, ResourceType.INSTANCE, s1, Collections.singletonList(vo)));
	}

	@Test
	void createNotExistingSubscription() {
		final var server1 = qiRepository.findByName("server1").getId();
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.update(0, ResourceType.INSTANCE, server1, Collections.emptyList()));
	}

	@Test
	void createNotExistingResource() {
		final var server1 = qiRepository.findByName("server1").getId();

		final var vo = new NetworkVo();
		vo.setPeer(0);
		vo.setPeerType(ResourceType.INSTANCE);
		vo.setPort(1);

		Assertions.assertThrows(JpaObjectRetrievalFailureException.class, () -> networkResource.update(subscription,
				ResourceType.INSTANCE, server1, Collections.singletonList(vo)));
	}

}
