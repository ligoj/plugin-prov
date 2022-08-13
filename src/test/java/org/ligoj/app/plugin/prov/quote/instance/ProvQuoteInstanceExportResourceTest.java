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
import org.ligoj.app.plugin.prov.Floating;
import org.ligoj.app.plugin.prov.model.ProvBudget;
import org.ligoj.app.plugin.prov.model.ProvContainerPrice;
import org.ligoj.app.plugin.prov.model.ProvContainerType;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.app.plugin.prov.model.ProvDatabasePrice;
import org.ligoj.app.plugin.prov.model.ProvDatabaseType;
import org.ligoj.app.plugin.prov.model.ProvFunctionPrice;
import org.ligoj.app.plugin.prov.model.ProvFunctionType;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvOptimizer;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteContainer;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteFunction;
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
import org.ligoj.app.plugin.prov.quote.upload.ProvQuoteUploadResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

/**
 * Test class of {@link ProvQuoteInstanceExportResource}
 */
class ProvQuoteInstanceExportResourceTest extends AbstractProvResourceTest {

	@Autowired
	private ProvQuoteInstanceExportResource qieResource;

	@Autowired
	private ProvQuoteUploadResource qiuResource;

	@Override
	@BeforeEach
	protected void prepareData() throws IOException {
		// Only with Spring context
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, Subscription.class, ProvLocation.class, ProvCurrency.class,
						ProvQuote.class, ProvUsage.class, ProvBudget.class, ProvOptimizer.class, ProvStorageType.class,
						ProvStoragePrice.class, ProvInstancePriceTerm.class, ProvInstanceType.class,
						ProvInstancePrice.class, ProvQuoteInstance.class, ProvSupportType.class, ProvSupportPrice.class,
						ProvQuoteSupport.class },
				StandardCharsets.UTF_8.name());
		csvForJpa.insert("csv/database", new Class[] { ProvDatabaseType.class, ProvDatabasePrice.class,
				ProvQuoteDatabase.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		csvForJpa.insert("csv/container", new Class[] { ProvContainerType.class, ProvContainerPrice.class,
				ProvQuoteContainer.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());
		csvForJpa.insert("csv/function", new Class[] { ProvFunctionType.class, ProvFunctionPrice.class,
				ProvQuoteFunction.class, ProvQuoteStorage.class }, StandardCharsets.UTF_8.name());

		preparePostData();

		var entity = new ProvTag();
		entity.setName("key");
		entity.setValue("value");
		entity.setResource(qiRepository.findByName("server1").getId());
		entity.setType(ResourceType.INSTANCE);
		entity.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(entity);

		entity = new ProvTag();
		entity.setName("key3");
		entity.setResource(qiRepository.findByName("server1").getId());
		entity.setType(ResourceType.INSTANCE);
		entity.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(entity);

		entity = new ProvTag();
		entity.setName("key2");
		entity.setResource(qbRepository.findByName("database1").getId());
		entity.setType(ResourceType.DATABASE);
		entity.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(entity);
		em.flush();
		em.clear();
	}

	@Test
	void exportInline() throws IOException {
		// Force physical for server2
		qiRepository.findByName("server2").getPrice().getType().setPhysical(true);
		qiRepository.findByName("server2").setPhysical(true);

		final var lines = export();
		Assertions.assertEquals(24, lines.size());

		// Header
		Assertions.assertEquals(
				"resource-type;name;cpu;cpuMax;gpu;gpuMax;ram;ramMax;os;usage;budget;optimizer;term;location;min;max"
						+ ";maxvariablecost;workload;processor;physical;ephemeral;type;engine;edition;internet;"
						+ "license;cost;tags" + ";disk;diskMax;diskType;diskLatency;diskOptimized;diskCost;diskTags"
						+ ";disk1;disk1Max;disk1Type;disk1Latency;disk1Optimized;disk1Cost;disk1Tags"
						+ ";disk2;disk2Max;disk2Type;disk2Latency;disk2Optimized;disk2Cost;disk2Tags",
				lines.get(0));

		// Instance with multiple disks
		Assertions.assertEquals(
				"INSTANCE;server1;0,5;;0;;2000;;LINUX;;;;on-demand1;;2;10;10,1;100;;;false;instance1;;;PUBLIC;;292,8;key:value,key3"
						+ ";20;;storage1;GOOD;IOPS;8,4;" + ";10;;storage2;MEDIUM;THROUGHPUT;155,6;"
						+ ";51;;storage2;MEDIUM;THROUGHPUT;155,6;",
				lines.get(1));

		// Instance without disk
		Assertions.assertEquals(
				"INSTANCE;server2;0,25;;0;;1000;;LINUX;;;;on-demand2;;1;1;;;Intel;true;true;instance1;;;PRIVATE;;128,1;",
				lines.get(7));
		Assertions.assertEquals(
				"INSTANCE;server4;1;;0;;2000;;DEBIAN;;;;on-demand1;;1;1;;15;;;false;instance3;;;PUBLIC;;292,8;",
				lines.get(3));

		// Container
		Assertions.assertEquals(
				"CONTAINER;container1;0,5;;0;;2000;;LINUX;;;;on-demand1;;1;2;;100;;;false;container1;;;PUBLIC;;116,3;"
						+ ";20;;storage1;GOOD;IOPS;4,2;" + ";51;;storage2;MEDIUM;THROUGHPUT;77,8;"
						+ ";20;19;storage5-database;GOOD;IOPS;30;",
				lines.get(15));

		// Function
		Assertions.assertEquals("FUNCTION;function1;0,5;;0;;2000;;Python;;;;on-demand1;;1.0;;;100;;;false;function1;;;"
				+ "PUBLIC;;116,3;;20;;storage1;GOOD;IOPS;4,2;;51;;storage2;MEDIUM;THROUGHPUT;"
				+ "77,8;;20;19;storage5-database;GOOD;IOPS;30;", lines.get(22));

		// Database
		Assertions.assertEquals(
				"DATABASE;database1;0,5;;0;;2000;;;;;;on-demand1;;1;2;;100;;;;database1;MYSQL;;PUBLIC;;116,3;key2"
						+ ";20;19;storage5-database;GOOD;IOPS;30;",
				lines.get(8));
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
		qcResource.deleteAll(subscription);
		qfResource.deleteAll(subscription);
		var configuration = getConfiguration();
		checkCost(configuration.getCost(), 0, 0, false);

		// Export (only headers)
		Assertions.assertEquals(1, export().size());

		// Import
		qiuResource.upload(subscription, new ClassPathResource("csv/upload/upload-with-headers.csv").getInputStream(),
				null, true, "Full Time 12 month", null, null, 1);
		em.flush();
		em.clear();
		resource.refresh(subscription);
		configuration = getConfiguration();
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);

		// Export
		final var lines = export();
		Assertions.assertEquals(12, lines.size());
		Assertions.assertEquals(
				"INSTANCE;JIRA;4;;0;;6000;;LINUX;Full Time 12 month;;;on-demand1;;1;1;10,1;100;;false;false;dynamic;;;"
						+ "PRIVATE;;990,862;;270;;storage1;GOOD;;56,7;",
				lines.get(1));
		configuration = getConfiguration();
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);

		// Empty
		em.flush();
		em.clear();
		qsResource.deleteAll(subscription);
		qiResource.deleteAll(subscription);
		qbResource.deleteAll(subscription);
		configuration = getConfiguration();
		checkCost(configuration.getCost(), 0, 0, false);

		// Import
		qiuResource.upload(subscription, IOUtils.toInputStream(String.join("\n", lines), "UTF-8"), null, true,
				"Full Time 12 month", null, null, 1);
		configuration = getConfiguration();

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
		// 7 VM + 7 DB + 7 Container + 9 Disks + 1 Support + 1 Header + 6 Function
		Assertions.assertEquals(38, lines.size());

		// Header
		Assertions.assertEquals(
				"name;cpu;cpuMax;gpu;gpuMax;ram;ramMax;os;usage;buget;optimizer;term;location;min;max;maxvariablecost;workload;"
						+ "processor;physical;ephemeral;type;internet;license;cost;tags;disk;diskMax;instance;database;"
						+ "latency;optimized;engine;edition;seats",
				lines.get(0));

		// Instance data
		Assertions.assertEquals(
				"server1;0,5;;0;;2000;;LINUX;;;;on-demand1;;2;10;10,1;100;;;false;instance1;PUBLIC;;292,8;key:value,key3",
				lines.get(1));

		// Container data
		Assertions.assertEquals(
				"container1;0,5;;0;;2000;;LINUX;;;;on-demand1;;1;2;;100;;;false;container1;PUBLIC;;116,3;",
				lines.get(8));

		// Function data
		Assertions.assertEquals("function2;0,25;;0;;1000;;Python;;;;1y;;1.0;;;;;;false;function1;PUBLIC;;89,5;",
				lines.get(16));

		// Database data
		Assertions.assertEquals("database2;0,25;;0;;1000;;;;;;1y;;1;1;;;;;;database1;;;89,5;;;;;;;MYSQL;",
				lines.get(18));
		Assertions.assertEquals(
				"database4;0,5;;0;;2000;;;;;;on-demand1;;1;1;;;;;;database2;;;135,42;;;;;;;ORACLE;STANDARD ONE",
				lines.get(20));

		// Storage data
		Assertions.assertEquals("server1-data;;;;;;;;;;;;;;;;;;;;storage2;;;155,6;;10;;server1;;MEDIUM;THROUGHPUT",
				lines.get(25));

		// Support data
		Assertions.assertEquals("support-name1;;;;;;;;;;;;;;;;;;;;support2;;;640,789;;;;;;;;;1", lines.get(37));
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
	protected Floating updateCost() {
		// Check the cost fully updated and exact actual cost
		final var cost = resource.updateCost(subscription);
		checkCost(cost, 9016.567, 12221.692, false);
		checkCost(subscription, 9016.567, 12221.692, false);
		em.flush();
		em.clear();
		return cost;
	}
}
