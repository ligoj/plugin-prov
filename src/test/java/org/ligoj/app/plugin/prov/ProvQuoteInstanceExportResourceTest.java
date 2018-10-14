/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
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
	private ProvQuoteInstanceUploadResource qiuResource;

	@Test
	public void exportInline() throws IOException {
		final List<String> lines = export();
		Assertions.assertEquals(8, lines.size());

		// Header
		Assertions.assertEquals(
				"name;cpu;ram;os;usage;term;location;min;max;maxvariablecost;constant;ephemeral;type;internet;license;cost"
						+ ";disk;diskType;diskLatency;diskOptimized;diskCost"
						+ ";disk1;disk1Type;disk1Latency;disk1Optimized;disk1Cost"
						+ ";disk2;disk2Type;disk2Latency;disk2Optimized;disk2Cost",
				lines.get(0));

		// Instance with multiple disks
		Assertions.assertEquals("server1;0.5;2000;LINUX;;on-demand1;;2;10;10.1;true;false;instance1;PUBLIC;;292.8"
				+ ";20;storage1;GOOD;IOPS;8.4" + ";10;storage2;MEDIUM;THROUGHPUT;155.6"
				+ ";51;storage2;MEDIUM;THROUGHPUT;155.6", lines.get(1));

		// Instance without disk
		Assertions.assertEquals("server2;0.25;1000;LINUX;;on-demand2;;1;1;;;true;instance1;PRIVATE;;128.1",
				lines.get(2));
		Assertions.assertEquals("server4;1.0;2000;DEBIAN;;on-demand1;;1;1;;false;false;instance3;PUBLIC;;292.8",
				lines.get(4));
	}

	/**
	 * Full round trip: empty, export, import, export, import.
	 */
	@Test
	public void exportInlineImport() throws IOException {
		// Empty
		qiResource.deleteAll(subscription);
		qsResource.deleteAll(subscription);
		em.flush();
		em.clear();
		QuoteVo configuration = resource.getConfiguration(subscription);
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
		final List<String> lines = export();
		Assertions.assertEquals(12, lines.size());
		Assertions.assertEquals(
				"JIRA;4.0;6000;LINUX;Full Time 12 month;on-demand1;;1;1;10.1;true;false;dynamic;PRIVATE;;990.861552"
						+ ";270;storage1;GOOD;;56.7",
				lines.get(1));
		em.flush();
		em.clear();
		configuration = resource.getConfiguration(subscription);
		checkCost(configuration.getCost(), 9879.288, 9879.288, false);

		// Empty
		em.flush();
		em.clear();
		qiResource.deleteAll(subscription);
		qsResource.deleteAll(subscription);
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
		final List<String> lines2 = export();
		Assertions.assertEquals(12, lines.size());
		Assertions.assertEquals(lines, lines2);
	}

	@Test
	public void exportSplit() throws IOException {
		final StreamingOutput export = (StreamingOutput) qieResource.exportSplit(subscription, "test.csv").getEntity();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		export.write(out);
		final BufferedReader inputStreamReader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), "cp1252"));
		final List<String> lines = IOUtils.readLines(inputStreamReader);
		Assertions.assertEquals(12, lines.size()); // 8 VM + 4 Disks

		// Header
		Assertions.assertEquals(
				"name;cpu;ram;os;usage;term;location;min;max;maxvariablecost;constant;ephemeral;type;internet;license;cost"
						+ ";disk;instance;latency;optimized",
				lines.get(0));

		// Instance data
		Assertions.assertEquals("server1;0.5;2000;LINUX;;on-demand1;;2;10;10.1;true;false;instance1;PUBLIC;;292.8",
				lines.get(1));

		// Storage data
		Assertions.assertEquals("server1-temp;;;;;;;;;;;;155.6;;51;storage2;server1;MEDIUM;THROUGHPUT", lines.get(10));
	}

	private List<String> export() throws IOException {
		final StreamingOutput export = (StreamingOutput) qieResource.exportInline(subscription, "test.csv").getEntity();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		export.write(out);
		final BufferedReader inputStreamReader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), "cp1252"));
		return IOUtils.readLines(inputStreamReader);
	}
}
