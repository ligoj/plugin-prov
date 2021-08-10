/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov.quote.instance;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.transaction.Transactional;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.collections4.CollectionUtils;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.TagVo;
import org.ligoj.app.plugin.prov.model.AbstractQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
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
	 * @param subscription The subscription identifier.
	 * @param file         The user file name to use in download response.
	 * @return the stream ready to be read during the serialization.
	 */
	@GET
	@Path("{subscription:\\d+}/{file:.*-instances-inline-storage-.*\\.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response exportInline(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) {
		final var vo = resource.getConfiguration(subscriptionResource.checkVisible(subscription));
		return AbstractToolPluginResource.download(output -> {
			try (var writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
				final var qsByQi = new HashMap<Integer, List<ProvQuoteStorage>>();
				final var qsByQb = new HashMap<Integer, List<ProvQuoteStorage>>();
				final var qsByQc = new HashMap<Integer, List<ProvQuoteStorage>>();
				final var qsByQf = new HashMap<Integer, List<ProvQuoteStorage>>();
				final var itags = vo.getTags().get(ResourceType.INSTANCE);
				final var dtags = vo.getTags().get(ResourceType.DATABASE);
				final var ctags = vo.getTags().get(ResourceType.CONTAINER);
				final var ftags = vo.getTags().get(ResourceType.FUNCTION);
				final var stags = vo.getTags().get(ResourceType.STORAGE);
				vo.getStorages().stream().filter(qs -> qs.getInstance() != null)
						.forEach(qs -> qsByQi.computeIfAbsent(qs.getInstance(), ArrayList::new).add(qs));
				vo.getStorages().stream().filter(qs -> qs.getDatabase() != null)
						.forEach(qs -> qsByQb.computeIfAbsent(qs.getDatabase(), ArrayList::new).add(qs));
				vo.getStorages().stream().filter(qs -> qs.getContainer() != null)
						.forEach(qs -> qsByQc.computeIfAbsent(qs.getContainer(), ArrayList::new).add(qs));
				vo.getStorages().stream().filter(qs -> qs.getFunction() != null)
						.forEach(qs -> qsByQf.computeIfAbsent(qs.getFunction(), ArrayList::new).add(qs));
				final var max = Stream.of(qsByQi, qsByQb, qsByQc)
						.mapToInt(qsBy -> qsBy.values().stream().mapToInt(List::size).max().orElse(0)).max().orElse(0);

				// Minimal headers
				writer.format("%s" + ";%s".repeat(30), "resource-type", "name", "cpu", "cpuMax", "ram", "ramMax", "os",
						"usage", "term", "location", "min", "max", "maxvariablecost", "constant", "processor",
						"physical", "ephemeral", "type", "engine", "edition", "internet", "license", "cost", "tags",
						"disk", "diskMax", "diskType", "diskLatency", "diskOptimized", "diskCost", "diskTags");

				// Additional headers for storages above the first one
				IntStream.range(1, max)
						.forEach(i -> writer.format(
								";disk%d;disk%dMax;disk%dType;disk%dLatency;disk%dOptimized;disk%dCost;disk%dTags", i,
								i, i, i, i, i, i));
				vo.getInstances().forEach(qi -> {
					// Write quote instance
					writer.format("\n%s" + ";%s".repeat(23), ResourceType.INSTANCE, toString(qi), toString(qi.getCpu()),
							toString(qi.getCpuMax()), toString(qi.getRam()), toString(qi.getRamMax()), qi.getOs(),
							toString(qi.getUsage()), toString(qi.getPrice().getTerm()), toString(qi.getLocation()),
							qi.getMinQuantity(), toString(qi.getMaxQuantity()), toString(qi.getMaxVariableCost()),
							toString(qi.getConstant()), toString(qi.getProcessor()), toString(qi.getPhysical()),
							qi.isEphemeral(), toType(qi), "", "", qi.getInternet(), toString(qi.getLicense()),
							toString(qi.getCost()), toString(qi, itags));
					writeStorage(writer, qsByQi, stags, qi.getId());
				});

				vo.getDatabases().forEach(qi -> {
					// Write quote database
					writer.format("\n%s" + ";%s".repeat(23), ResourceType.DATABASE, toString(qi), toString(qi.getCpu()),
							toString(qi.getCpuMax()), toString(qi.getRam()), toString(qi.getRamMax()), "",
							toString(qi.getUsage()), toString(qi.getPrice().getTerm()), toString(qi.getLocation()),
							qi.getMinQuantity(), toString(qi.getMaxQuantity()), "", toString(qi.getConstant()),
							toString(qi.getProcessor()), toString(qi.getPhysical()), "", toType(qi), qi.getEngine(),
							toString(qi.getEdition()), qi.getInternet(), toString(qi.getLicense()),
							toString(qi.getCost()), toString(qi, dtags));
					writeStorage(writer, qsByQb, stags, qi.getId());
				});

				vo.getContainers().forEach(qc -> {
					// Write quote container
					writer.format("\n%s" + ";%s".repeat(23), ResourceType.CONTAINER, toString(qc),
							toString(qc.getCpu()), toString(qc.getCpuMax()), toString(qc.getRam()),
							toString(qc.getRamMax()), qc.getOs(), toString(qc.getUsage()),
							toString(qc.getPrice().getTerm()), toString(qc.getLocation()), qc.getMinQuantity(),
							toString(qc.getMaxQuantity()), toString(qc.getMaxVariableCost()),
							toString(qc.getConstant()), toString(qc.getProcessor()), toString(qc.getPhysical()),
							qc.isEphemeral(), toType(qc), "", "", qc.getInternet(), toString(qc.getLicense()),
							toString(qc.getCost()), toString(qc, ctags));
					writeStorage(writer, qsByQc, stags, qc.getId());
				});

				vo.getFunctions().forEach(qf -> {
					// Write quote function
					writer.format("\n%s" + ";%s".repeat(23), ResourceType.FUNCTION, toString(qf), toString(qf.getCpu()),
							toString(qf.getCpuMax()), toString(qf.getRam()), toString(qf.getRamMax()), qf.getRuntime(),
							toString(qf.getUsage()), toString(qf.getPrice().getTerm()), toString(qf.getLocation()),
							qf.getNbRequests(), "", "", toString(qf.getConstant()), toString(qf.getProcessor()),
							toString(qf.getPhysical()), qf.isEphemeral(), toType(qf), "", "", qf.getInternet(), "",
							toString(qf.getCost()), toString(qf, ftags));
					writeStorage(writer, qsByQf, stags, qf.getId());
				});
				writer.flush();
			}
		}, file).build();
	}

	/**
	 * Write related storages
	 */
	private void writeStorage(final PrintWriter writer, final Map<Integer, List<ProvQuoteStorage>> qsByQi,
			final Map<Integer, List<TagVo>> stags, Integer qi) {
		qsByQi.getOrDefault(qi, Collections.emptyList())
				.forEach(qs -> writer.format(";%s".repeat(7), qs.getSize(), toString(qs.getSizeMax()), toType(qs),
						toString(qs.getLatency()), toString(qs.getOptimized()), toString(qs.getCost()),
						toString(qs, stags)));
	}

	/**
	 * Return the instances, databases, support and storage quotes in CSV format. Some columns may be <code>null</code>
	 * depending on the related resource.
	 *
	 * @param subscription The subscription identifier.
	 * @param file         The user file name to use in download response.
	 * @return the stream ready to be read during the serialization.
	 */
	@GET
	@Path("{subscription:\\d+}/{file:.*-split-.*\\.csv}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response exportSplit(@PathParam("subscription") final int subscription,
			@PathParam("file") final String file) {
		final var vo = resource.getConfiguration(subscriptionResource.checkVisible(subscription));
		return AbstractToolPluginResource.download(output -> {
			try (var writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
				writer.format("%s" + ";%s".repeat(29), "name", "cpu", "cpuMax", "ram", "ramMax", "os", "usage", "term",
						"location", "min", "max", "maxvariablecost", "constant", "processor", "physical", "ephemeral",
						"type", "internet", "license", "cost", "tags", "disk", "diskMax", "instance", "database",
						"latency", "optimized", "engine", "edition", "seats");

				// Write quote instances
				final var itags = vo.getTags().get(ResourceType.INSTANCE);
				vo.getInstances()
						.forEach(qi -> writer.format("\n%s" + ";%s".repeat(20), toString(qi), toString(qi.getCpu()),
								toString(qi.getCpuMax()), toString(qi.getRam()), toString(qi.getRamMax()), qi.getOs(),
								toString(qi.getUsage()), toString(qi.getPrice().getTerm()), toString(qi.getLocation()),
								qi.getMinQuantity(), toString(qi.getMaxQuantity()), toString(qi.getMaxVariableCost()),
								toString(qi.getConstant()), toString(qi.getProcessor()), toString(qi.getPhysical()),
								qi.isEphemeral(), toType(qi), qi.getInternet(), toString(qi.getLicense()),
								toString(qi.getCost()), toString(qi, itags)));

				// Write quote containers
				final var ctags = vo.getTags().get(ResourceType.CONTAINER);
				vo.getContainers()
						.forEach(qc -> writer.format("\n%s" + ";%s".repeat(20), toString(qc), toString(qc.getCpu()),
								toString(qc.getCpuMax()), toString(qc.getRam()), toString(qc.getRamMax()), qc.getOs(),
								toString(qc.getUsage()), toString(qc.getPrice().getTerm()), toString(qc.getLocation()),
								qc.getMinQuantity(), toString(qc.getMaxQuantity()), toString(qc.getMaxVariableCost()),
								toString(qc.getConstant()), toString(qc.getProcessor()), toString(qc.getPhysical()),
								qc.isEphemeral(), toType(qc), qc.getInternet(), toString(qc.getLicense()),
								toString(qc.getCost()), toString(qc, ctags)));
				// Write quote functions
				final var ftags = vo.getTags().get(ResourceType.FUNCTION);
				vo.getFunctions()
						.forEach(qf -> writer.format("\n%s" + ";%s".repeat(20), toString(qf), toString(qf.getCpu()),
								toString(qf.getCpuMax()), toString(qf.getRam()), toString(qf.getRamMax()),
								qf.getRuntime(), toString(qf.getUsage()), toString(qf.getPrice().getTerm()),
								toString(qf.getLocation()), qf.getNbRequests(), "", "", toString(qf.getConstant()),
								toString(qf.getProcessor()), toString(qf.getPhysical()), qf.isEphemeral(), toType(qf),
								qf.getInternet(), "", toString(qf.getCost()), toString(qf, ftags)));

				// Write quote databases
				final var dtags = vo.getTags().get(ResourceType.DATABASE);
				vo.getDatabases().forEach(qi -> writer.format("\n%s" + ";%s".repeat(20) + ";;;;;;%s;%s", toString(qi),
						toString(qi.getCpu()), toString(qi.getCpuMax()), toString(qi.getRam()),
						toString(qi.getRamMax()), "", toString(qi.getUsage()), toString(qi.getPrice().getTerm()),
						toString(qi.getLocation()), qi.getMinQuantity(), toString(qi.getMaxQuantity()), "",
						toString(qi.getConstant()), toString(qi.getProcessor()), toString(qi.getPhysical()), "",
						toType(qi), "", toString(qi.getLicense()), toString(qi.getCost()), toString(qi, dtags),
						qi.getEngine(), toString(qi.getEdition())));

				// Write quote storages
				final var stags = vo.getTags().get(ResourceType.STORAGE);
				vo.getStorages().forEach(qs -> writer.format("\n%s;;;;;;;;;%s;;;;;;;%s;;" + ";%s".repeat(8),
						toString(qs), toString(qs.getLocation()), toType(qs), toString(qs.getCost()),
						toString(qs, stags), qs.getSize(), toString(qs.getSizeMax()), toString(qs.getQuoteInstance()),
						toString(qs.getQuoteDatabase()), toString(qs.getLatency()), toString(qs.getOptimized())));

				// Write quote support
				final var s2tags = vo.getTags().get(ResourceType.SUPPORT);
				vo.getSupports().forEach(qs -> writer.format("\n%s;;;;;;;;;;;;;;;;%s;;;%s;%s;;;;;;;;%s", toString(qs),
						toType(qs), toString(qs.getCost()), toString(qs, s2tags), toString(qs.getSeats())));
				writer.flush();
			}
		}, file).build();
	}

	/**
	 * Return the name of a quote resource.
	 */
	private String toType(final AbstractQuote<?> resource) {
		return resource.getPrice().getType().getCode();
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

	private String toString(final INamableBean<Integer> qi, final Map<Integer, List<TagVo>> tags) {
		return tags == null ? "" : toString(tags.get(qi.getId()));
	}

	/**
	 * Return the 'toString' of a nullable collection.
	 */
	private String toString(final Collection<?> optional) {
		return CollectionUtils.emptyIfNull(optional).stream().map(Object::toString).collect(Collectors.joining(","));
	}

	/**
	 * Return the 'toDecimal' of a nullable object.
	 */
	private String toString(final Double optional) {
		return optional == null ? ""
				: String.valueOf(FloatingCost.round(optional)).replace('.', ',').replaceFirst(",0$", "");
	}
}
