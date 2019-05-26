/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.AbstractProvResourceTest;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvQuoteSupport;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvTag;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.plugin.prov.quote.support.ProvQuoteSupportResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Test class of {@link ProvQuoteInstanceExportResource}
 */
public class ProvQuoteInstanceExportResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteInstanceExportResource qieResource;
	@Autowired
	private ProvQuoteInstanceResource qiResource;
	@Autowired
	private ProvQuoteStorageResource qsResource;
	@Autowired
	private ProvQuoteDatabaseResource qbResource;
	@Autowired
	private ProvQuoteSupportResource qs2Resource;
	@Autowired
	private ProvQuoteInstanceRepository qiRepository;
	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;
	@Autowired
	private ProvQuoteRepository qRepository;

	@Autowired
	private ProvQuoteInstanceUploadResource qiuResource;

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv", new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class,
				ProvCurrency.class, ProvQuote.class, ProvUsage.class, ProvStorageType.class, ProvStoragePrice.class,
				ProvInstancePriceTerm.class, ProvInstanceType.class, ProvInstancePrice.class, ProvQuoteInstance.class,
				ProvSupportType.class, ProvSupportPrice.class, ProvQuoteSupport.class }, StandardCharsets.UTF_8.name());
		persistEntities("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		subscription = getSubscription("gStack", ProvResource.SERVICE_KEY);

		var entity = new ProvTag();
		entity.setName("key");
		entity.setValue("value");
		entity.setResource(qiRepository.findByName("server1").getId());
		entity.setType(ResourceType.INSTANCE);
		entity.setConfiguration(qRepository.findBy("subscription.id", subscription));
		em.persist(entity);

		entity = new ProvTag();
		entity.setName("key2");
		entity.setResource(qbRepository.findByName("database1").getId());
		entity.setType(ResourceType.DATABASE);
		entity.setConfiguration(qRepository.findBy("subscription.id", subscription));
		em.persist(entity);

		clearAllCache();
		updateCost();
	}

	@Test
	void exportInline() throws IOException {
		final var lines = export();
		Assertions.assertEquals(8, lines.size());

		// Header
		Assertions.assertEquals(
				"name;cpu;ram;os;usage;term;location;min;max;maxvariablecost;constant;ephemeral;type;internet;license;cost;tags"
						+ ";disk;diskType;diskLatency;diskOptimized;diskCost;diskTags"
						+ ";disk1;disk1Type;disk1Latency;disk1Optimized;disk1Cost;disk1Tags"
						+ ";disk2;disk2Type;disk2Latency;disk2Optimized;disk2Cost;disk2Tags",
				lines.get(0));

		// Instance with multiple disks
		Assertions.assertEquals("server1;0,5;2000;LINUX;;on-demand1;;2;10;10,1;true;false;instance1;PUBLIC;;292,8;key:value"
				+ ";20;storage1;GOOD;IOPS;8,4;" + ";10;storage2;MEDIUM;THROUGHPUT;155,6;"
				+ ";51;storage2;MEDIUM;THROUGHPUT;155,6;", lines.get(1));

		// Instance without disk
		Assertions.assertEquals("server2;0,25;1000;LINUX;;on-demand2;;1;1;;;true;instance1;PRIVATE;;128,1;",
				lines.get(2));
		Assertions.assertEquals("server4;1;2000;DEBIAN;;on-demand1;;1;1;;false;false;instance3;PUBLIC;;292,8;",
				lines.get(4));
	}

	/**
	 * Full round trip: empty, export, import, export, import.
	 */
	@Test
	void exportInlineImport() throws IOException {
		// Empty
		qsResource.deleteAll(subscription);
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		qs2Resource.deleteAll(subscription);
		em.flush();
		em.clear();
		var configuration = resource.getConfiguration(subscription);
		checkCost(configuration.getCost(), 0, 0, false);

		// Export (only headers)
		Assertions.assertEquals(1, export().size());

		// Import
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-with-headers.csv").getInputStream(),
				null, true, "Full Time 12 month", 1, "UTF-8");
		em.flush();
		em.clear();
		resource.refresh(subscription);
		em.flush();
		em.clear();
		configuration = resource.getConfiguration(subscription);
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);

		// Export
		final var lines = export();
		Assertions.assertEquals(12, lines.size());
		Assertions.assertEquals(
				"JIRA;4;6000;LINUX;Full Time 12 month;on-demand1;;1;1;10,1;true;false;dynamic;PRIVATE;;990,862"
						+ ";;270;storage1;GOOD;;56,7;",
				lines.get(1));
		em.flush();
		em.clear();
		configuration = resource.getConfiguration(subscription);
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);

		// Empty
		em.flush();
		em.clear();
		qsResource.deleteAll(subscription);
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		em.flush();
		em.clear();
		configuration = resource.getConfiguration(subscription);
		checkCost(configuration.getCost(), 0, 0, false);

		// Import
		qiuResource.upload(subscription, IOUtils.toInputStream(String.join("\n", lines), "UTF-8"), null, true,
				"Full Time 12 month", 1, "UTF-8");
		em.flush();
		em.clear();
		configuration = resource.getConfiguration(subscription);

		// Check backup restore succeed
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);
		final var lines2 = export();
		Assertions.assertEquals(12, lines.size());
		Assertions.assertEquals(lines, lines2);
	}

	@Test
	void exportSplit() throws IOException {
		final var export = (StreamingOutput) qieResource.exportSplit(subscription, "test.csv").getEntity();
		final var out = new ByteArrayOutputStream();
		export.write(out);
		final var inputStreamReader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), "cp1252"));
		final var lines = IOUtils.readLines(inputStreamReader);
		Assertions.assertEquals(21, lines.size()); // 8 VM + 7 DB + 4 Disks + 1 Support + 1 Header

		// Header
		Assertions.assertEquals(
				"name;cpu;ram;os;usage;term;location;min;max;maxvariablecost;constant;ephemeral;type;internet;license;cost;tags"
						+ ";disk;instance;database;latency;optimized;engine;edition;seats",
				lines.get(0));

		// Instance data
		Assertions.assertEquals("server1;0,5;2000;LINUX;;on-demand1;;2;10;10,1;true;false;instance1;PUBLIC;;292,8;key:value",
				lines.get(1));

		// Database data
		Assertions.assertEquals("database2;0,25;1000;;;1y;;1;1;;;;database1;;;89,5;;;;;;;MYSQL;", lines.get(9));
		Assertions.assertEquals("database4;0,5;2000;;;on-demand1;;1;1;;;;database2;;;135,42;;;;;;;ORACLE;STANDARD ONE",
				lines.get(11));

		// Storage data
		Assertions.assertEquals("server1-temp;;;;;;;;;;;;storage2;;;155,6;;;51;server1;;MEDIUM;THROUGHPUT", lines.get(17));

		// Support data
		Assertions.assertEquals("support-name1;;;;;;;;;;;;support2;;;577,26;;;;;;;;;1", lines.get(20));
	}

	private List<String> export() throws IOException {
		final var export = (StreamingOutput) qieResource.exportInline(subscription, "test.csv").getEntity();
		final var out = new ByteArrayOutputStream();
		export.write(out);
		final var inputStreamReader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), "cp1252"));
		return IOUtils.readLines(inputStreamReader);
	}

	@Override
	protected void updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		Assertions.assertEquals(7682.458, cost.getMin(), DELTA);
		Assertions.assertEquals(10408.153, cost.getMax(), DELTA);
		Assertions.assertFalse(cost.isUnbound());
		checkCost(subscription, 7682.458, 10408.153, false);
		em.flush();
		em.clear();
	}
}
