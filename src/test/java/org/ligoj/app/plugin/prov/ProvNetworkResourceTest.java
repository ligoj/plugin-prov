/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

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
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvNetwork;
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
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
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
class ProvNetworkResourceTest extends AbstractAppTest {

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
	private ProvStoragePriceRepository spRepository;

	@Autowired
	private ConfigurationResource configuration;

	@BeforeEach
	void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class<?>[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class, ProvQuoteStorage.class },
				StandardCharsets.UTF_8);
		subscription = getSubscription("Jupiter", ProvResource.SERVICE_KEY);
		configuration.put(ProvResource.USE_PARALLEL, "0");
		clearAllCache();
		resource.refresh(subscription);
	}

	private NetworkVo newVo(final boolean inbound, final int peer, final ResourceType type) {
		final var vo = new NetworkVo();
		vo.setInbound(inbound);
		fill(peer, type, vo);
		return vo;
	}

	private NetworkFullVo newFullVo(final int source, final ResourceType sourceType, final int peer,
			final ResourceType type) {
		final var vo = new NetworkFullVo();
		vo.setSource(source);
		vo.setSourceType(sourceType);
		fill(peer, type, vo);
		return vo;
	}

	private NetworkFullByNameVo newFullByNameVo(final String source, final String peer) {
		final var vo = new NetworkFullByNameVo();
		vo.setSource(source);
		vo.setPeer(peer);
		vo.setName("key");
		vo.setPort(1);
		vo.setRate(2);
		vo.setThroughput(3);
		return vo;
	}

	private void fill(final int peer, final ResourceType type, final NetworkVo vo) {
		vo.setName("key");
		vo.setPeer(peer);
		vo.setPeerType(type);
		vo.setPort(1);
		vo.setRate(2);
		vo.setThroughput(3);
	}

	private List<NetworkVo> prepare() {
		final var io = new ArrayList<NetworkVo>();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		io.add(newVo(true, server2, ResourceType.INSTANCE));
		io.add(newVo(false, storage1, ResourceType.STORAGE));
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		return io;
	}

	@Test
	void updateAllById() {
		final var io = new ArrayList<NetworkFullVo>();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var server3 = qiRepository.findByName("server3").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		io.add(newFullVo(server2, ResourceType.INSTANCE, server1, ResourceType.INSTANCE));
		io.add(newFullVo(server1, ResourceType.INSTANCE, storage1, ResourceType.STORAGE));
		io.add(newFullVo(server3, ResourceType.INSTANCE, storage1, ResourceType.STORAGE));
		networkResource.updateAllById(subscription, io);
		var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(3, list.size());
		assertLink0(server1, server2, list);
		assertLink1(server1, storage1, list);
		Assertions.assertEquals(server3, list.get(2).getSource());
		Assertions.assertEquals(storage1, list.get(2).getTarget());

		// Idempotent
		networkResource.updateAllById(subscription, io);
		list = networkRepository.findAll(subscription);
		Assertions.assertEquals(3, list.size());
		assertLink0(server1, server2, list);
		assertLink1(server1, storage1, list);
		Assertions.assertEquals(server3, list.get(2).getSource());
		Assertions.assertEquals(storage1, list.get(2).getTarget());

		// Remove all IO
		io.clear();
		networkResource.updateAllById(subscription, io);
		list = networkRepository.findAll(subscription);
		Assertions.assertEquals(0, list.size());
	}

	@Test
	void updateAllByName() {
		final var io = new ArrayList<NetworkFullByNameVo>();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var server3 = qiRepository.findByName("server3").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		io.add(newFullByNameVo("server2", "server1"));
		io.add(newFullByNameVo("server1", "server1-root"));
		io.add(newFullByNameVo("server3", "server1-root"));
		var errors = networkResource.updateAllByName(subscription, true, io);
		Assertions.assertEquals(0, errors);
		var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(3, list.size());
		assertLink0(server1, server2, list);
		assertLink1(server1, storage1, list);
		Assertions.assertEquals(server3, list.get(2).getSource());
		Assertions.assertEquals(storage1, list.get(2).getTarget());

		// Idempotent
		errors = networkResource.updateAllByName(subscription, true, io);
		Assertions.assertEquals(0, errors);
		list = networkRepository.findAll(subscription);
		Assertions.assertEquals(3, list.size());
		assertLink0(server1, server2, list);
		assertLink1(server1, storage1, list);
		Assertions.assertEquals(server3, list.get(2).getSource());
		Assertions.assertEquals(storage1, list.get(2).getTarget());

		// Remove all IO
		io.clear();
		errors = networkResource.updateAllByName(subscription, true, io);
		Assertions.assertEquals(0, errors);
		list = networkRepository.findAll(subscription);
		Assertions.assertEquals(0, list.size());
	}

	@Test
	void updateAllByNameAmbiguous() {
		final var storage1 = qsRepository.findByName("server1-root");
		storage1.setName("server1");
		em.merge(storage1);
		final var io = new ArrayList<NetworkFullByNameVo>();
		io.add(newFullByNameVo("server2", "server1"));
		Assertions.assertThrows(ValidationJsonException.class,
				() -> networkResource.updateAllByName(subscription, true, io));
	}

	@Test
	void updateAllByNameNotNetworkStorage() {
		final var storage1 = qsRepository.findByName("server1-root");
		storage1.setPrice(spRepository.findByExpected("code", "S3"));
		em.merge(storage1);
		final var io = new ArrayList<NetworkFullByNameVo>();
		io.add(newFullByNameVo("server2", "server1-root"));
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.updateAllByName(subscription, false, io));
	}

	@Test
	void updateAllByNameNotExistSource() {
		final var servers = Collections.singletonList(newFullByNameVo("serverYYY", "server1"));
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.updateAllByName(subscription, false, servers));
	}

	@Test
	void updateAllByNameNotExistSourceContinue() {
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();

		final var errors = networkResource.updateAllByName(subscription, true,
				Arrays.asList(newFullByNameVo("serverYYY", "server1"), newFullByNameVo("server1", "server2")));
		Assertions.assertEquals(1, errors);
		final var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(1, list.size());
		Assertions.assertEquals(server1, list.getFirst().getSource());
		Assertions.assertEquals(server2, list.getFirst().getTarget());
	}

	@Test
	void updateAllByNameNotExistTarget() {
		final var servers = Collections.singletonList(newFullByNameVo("server1", "serverYYY"));
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.updateAllByName(subscription, false, servers));
	}

	@Test
	void updateAllByNameNotExistTargetContinue() {
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();

		final var errors = networkResource.updateAllByName(subscription, true,
				Arrays.asList(newFullByNameVo("server1", "server2"), newFullByNameVo("server1", "serverYYY")));
		Assertions.assertEquals(1, errors);
		final var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(1, list.size());
		Assertions.assertEquals(server1, list.getFirst().getSource());
		Assertions.assertEquals(server2, list.getFirst().getTarget());
	}

	@Test
	void updateNotExistAllById() {
		final var io = new ArrayList<NetworkFullVo>();
		final var server1 = qiRepository.findByName("server1").getId();
		io.add(newFullVo(server1, ResourceType.INSTANCE, 0, ResourceType.INSTANCE));
		Assertions.assertThrows(EntityNotFoundException.class, () -> networkResource.updateAllById(subscription, io));
	}

	@Test
	void update() {
		final var io = prepare();
		final var server1 = qiRepository.findByName("server1").getId();
		final var server2 = qiRepository.findByName("server2").getId();
		final var server3 = qiRepository.findByName("server3").getId();
		final var storage1 = qsRepository.findByName("server1-root").getId();

		final var list = networkRepository.findAll(subscription);
		Assertions.assertEquals(2, list.size());
		assertLink0(server1, server2, list);
		assertLink1(server1, storage1, list);
		final var networks = resource.getConfiguration(subscription).getNetworks();
		Assertions.assertEquals(2, networks.size());
		Assertions.assertEquals(1, networks.getFirst().getPort());
		Assertions.assertEquals("key", list.getFirst().getName());
		Assertions.assertNotNull(list.getFirst().getConfiguration());

		// Replace the IO links to only one link server1->server3
		io.clear();
		io.add(newVo(false, server3, ResourceType.INSTANCE));
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		final var list2 = networkRepository.findAll(subscription);
		Assertions.assertEquals(1, list2.size());
		Assertions.assertEquals(server1, list2.getFirst().getSource());
		Assertions.assertEquals(server3, list2.getFirst().getTarget());
		Assertions.assertEquals(1, resource.getConfiguration(subscription).getNetworks().size());

		// Remove all IO
		io.clear();
		networkResource.update(subscription, ResourceType.INSTANCE, server1, io);
		Assertions.assertEquals(0, networkRepository.findAll(subscription).size());
		Assertions.assertEquals(0, resource.getConfiguration(subscription).getNetworks().size());
	}

	private void assertLink1(final Integer server1, final Integer storage1, final List<ProvNetwork> list) {
		Assertions.assertEquals(server1, list.get(1).getSource());
		Assertions.assertEquals(ResourceType.INSTANCE, list.get(1).getSourceType());
		Assertions.assertEquals("key", list.get(1).getName());
		Assertions.assertEquals(1, list.get(1).getPort());
		Assertions.assertEquals(2, list.get(1).getRate());
		Assertions.assertEquals(3, list.get(1).getThroughput());
		Assertions.assertEquals(storage1, list.get(1).getTarget());
		Assertions.assertEquals(ResourceType.STORAGE, list.get(1).getTargetType());
	}

	private void assertLink0(final Integer server1, final Integer server2, final List<ProvNetwork> list) {
		Assertions.assertEquals(server2, list.getFirst().getSource());
		Assertions.assertEquals(ResourceType.INSTANCE, list.getFirst().getSourceType());
		Assertions.assertEquals(1, list.getFirst().getPort());
		Assertions.assertEquals(2, list.getFirst().getRate());
		Assertions.assertEquals(3, list.getFirst().getThroughput());
		Assertions.assertEquals(server1, list.getFirst().getTarget());
		Assertions.assertEquals(ResourceType.INSTANCE, list.getFirst().getTargetType());
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

		final var servers = Collections.singletonList(vo);
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.update(subscription, ResourceType.INSTANCE, s1, servers));
	}

	@Test
	void createNotExistingSubscription() {
		final var server1 = qiRepository.findByName("server1").getId();
		final List<NetworkVo> servers = Collections.emptyList();
		Assertions.assertThrows(EntityNotFoundException.class,
				() -> networkResource.update(0, ResourceType.INSTANCE, server1, servers));
	}

	@Test
	void createNotExistingResource() {
		final var server1 = qiRepository.findByName("server1").getId();

		final var vo = new NetworkVo();
		vo.setPeer(0);
		vo.setPeerType(ResourceType.INSTANCE);
		vo.setPort(1);

		final var servers = Collections.singletonList(vo);
		Assertions.assertThrows(JpaObjectRetrievalFailureException.class,
				() -> networkResource.update(subscription, ResourceType.INSTANCE, server1, servers));
	}

}
