/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.resource.plugin.AbstractToolPluginResource;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.INamableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The instance part of the provisioning export to CSV file.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteInstanceExportResource {

	@Autowired
	protected ProvResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	/**
	 * Return the instance quotes with attached storages in CSV format. Sole storages are not included.
	 *
	 * @param subscription
	 *            The subscription identifier.
	 * @param file
	 *            The user file name to use in download response.
	 * @return the stream ready to be read during the serialization.
	 */
	@GET
	@Path("{subscription:\\d+}/{file:.*-instances-inline-storage-.*\\.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response exportInline(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) {
		final QuoteVo vo = resource.getConfiguration(subscriptionResource.checkVisible(subscription));
		return AbstractToolPluginResource.download(output -> {
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
				final Map<Integer, List<ProvQuoteStorage>> qsByQi = new HashMap<>();
				vo.getStorages().stream().filter(qs -> qs.getQuoteResource() != null)
						.forEach(qs -> qsByQi.computeIfAbsent(qs.getQuoteResource().getId(), ArrayList::new).add(qs));
				final int max = qsByQi.values().stream().mapToInt(List::size).max().orElse(0);

				// Minimal headers
				writer.format("%s" + StringUtils.repeat(";%s", 20), "name", "cpu", "ram", "os", "usage", "term",
						"location", "min", "max", "maxvariablecost", "constant", "ephemeral", "type", "internet",
						"license", "cost", "disk", "diskType", "diskLatency", "diskOptimized", "diskCost");

				// Additional headers for storages above the first one
				IntStream.range(1, max).forEach(i -> writer.print(";disk" + i + ";disk" + i + "Type" + ";disk" + i
						+ "Latency" + ";disk" + i + "Optimized" + ";disk" + i + "Cost"));
				vo.getInstances().forEach(qi -> {
					// Write quote instance
					writer.format("\n%s" + StringUtils.repeat(";%s", 15), toString(qi), toString(qi.getCpu()),
							toString(qi.getRam()), qi.getOs(), toString(qi.getUsage()),
							toString(qi.getPrice().getTerm()), toString(qi.getLocation()), qi.getMinQuantity(),
							toString(qi.getMaxQuantity()), toString(qi.getMaxVariableCost()),
							toString(qi.getConstant()), qi.isEphemeral(), toString(qi.getPrice().getType()),
							qi.getInternet(), toString(qi.getLicense()), toString(qi.getCost()));
					// Write related storage
					qsByQi.getOrDefault(qi.getId(), Collections.emptyList())
							.forEach(qs -> writer.format(StringUtils.repeat(";%s", 5), qs.getSize(),
									toString(qs.getPrice().getType()), toString(qs.getLatency()),
									toString(qs.getOptimized()), toString(qs.getCost())));
				});
				writer.flush();
			}
		}, file).build();
	}

	/**
	 * Return the instance and storage quotes in CSV format. Some columns may be <code>null</code> depending on the
	 * related resource.
	 *
	 * @param subscription
	 *            The subscription identifier.
	 * @param file
	 *            The user file name to use in download response.
	 * @return the stream ready to be read during the serialization.
	 */
	@GET
	@Path("{subscription:\\d+}/{file:.*-instances-split-storage-.*\\.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response exportSplit(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) {
		final QuoteVo vo = resource.getConfiguration(subscriptionResource.checkVisible(subscription));
		return AbstractToolPluginResource.download(output -> {
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
				writer.format("%s" + StringUtils.repeat(";%s", 20), "name", "cpu", "ram", "os", "usage", "term",
						"location", "min", "max", "maxvariablecost", "constant", "ephemeral", "type", "internet",
						"license", "cost", "disk", "instance", "database", "latency", "optimized");

				// Write quote instances
				vo.getInstances().forEach(qi -> {
					writer.format("\n%s" + StringUtils.repeat(";%s", 15), toString(qi), toString(qi.getCpu()),
							toString(qi.getRam()), qi.getOs(), toString(qi.getUsage()),
							toString(qi.getPrice().getTerm()), toString(qi.getLocation()), qi.getMinQuantity(),
							toString(qi.getMaxQuantity()), toString(qi.getMaxVariableCost()),
							toString(qi.getConstant()), qi.isEphemeral(), toString(qi.getPrice().getType()),
							qi.getInternet(), toString(qi.getLicense()), toString(qi.getCost()));
				});
				// Write quote storages
				vo.getStorages().forEach(qs -> writer.format("\n%s;;;;;;%s;;;;;;%s;;%s;%s;%s;%s;%s;%s", toString(qs),
						toString(qs.getLocation()), toString(qs.getCost()), qs.getSize(),
						toString(qs.getPrice().getType()), toString(qs.getQuoteInstance()),
						toString(qs.getQuoteDatabase()), toString(qs.getLatency()), toString(qs.getOptimized())));

				// Write quote support
				vo.getSupports().forEach(qs -> writer.format("\n%s;;;;;;;;;;;;%s;;%s;%s;;;", toString(qs),
						toString(qs.getCost()), toString(qs.getSeats()), toString(qs.getPrice().getType())));
				writer.flush();
			}
		}, file).build();
	}

	/**
	 * Return the name of a nullable object.
	 */
	private String toString(final INamableBean<?> optional) {
		return optional == null ? "" : optional.getName();
	}

	/**
	 * Return the 'toString' of a nullable object.
	 */
	private String toString(final Object optional) {
		return optional == null ? "" : optional.toString();
	}

	/**
	 * Return the 'toDecimal' of a nullable object.
	 */
	private String toString(final Double optional) {
		return optional == null ? ""
				: String.valueOf(Math.round(optional * 1000d) / 1000d).replace('.', ',').replaceFirst(",0$", "");
	}
}
