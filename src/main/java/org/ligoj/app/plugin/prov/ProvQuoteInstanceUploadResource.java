/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */

package org.ligoj.app.plugin.prov;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The instance part of the provisioning from upload CSV file.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteInstanceUploadResource {
	private static final List<String> MINIMAL_HEADERS = List.of("name", "cpu", "ram", "os");
	private static final String[] DEFAULT_HEADERS = { "name", "cpu", "ram", "os", "disk", "latency", "optimized" };

	/**
	 * Accepted headers. An array of string having this pattern: <code>name(:pattern)?</code>. Pattern part is optional.
	 */
	private static final List<String> ACCEPTED_HEADERS = List.of("name", "cpu:(vcpu|core|processor)s?", "ram:memory",
			"constant", "os:(system|operating system)", "disk:size", "latency", "optimized:(disk)?optimized",
			"type:instancetype", "internet", "minQuantity:min", "maxQuantity:max", "maxVariableCost:maxcost",
			"ephemeral:preemptive", "location:region", "usage:(use|env|environment)");

	/**
	 * Patterns from the most to the least exact match of header.
	 */
	private static final List<Function<String[], String>> MATCH_HEADERS = List.of(a -> a[0],
			a -> a.length == 1 ? a[0] : ("(" + a[0] + "|" + a[1] + ")"),
			a -> (a.length == 1 ? a[0] : ("(" + a[0] + "|" + a[1] + ")")) + ".*",
			a -> ".*" + (a.length == 1 ? a[0] : ("(" + a[0] + "|" + a[1] + ")")),
			a -> ".*" + (a.length == 1 ? a[0] : ("(" + a[0] + "|" + a[1] + ")")) + ".*");

	@Autowired
	private CsvForBean csvForBean;

	@Autowired
	protected ProvResource resource;

	@Autowired
	private ProvQuoteStorageResource storageResource;

	@Autowired
	private SubscriptionResource subscriptionResource;
	@Autowired
	private ProvUsageRepository usageRepository;

	@Autowired
	private ProvQuoteInstanceResource qResource;

	/**
	 * Check column's name, tying to match to valid headers. All rejected columns are dropped and replaced by an empty
	 * string <code>""</code>.
	 *
	 * @param headers
	 *            The given headers.
	 * @return The mapped and valid columns. Some may be empty and would be dropped.
	 */
	private String[] checkHeaders(final String... headers) {
		// Headers (K) mapped to input ones (V)
		final Map<String, String> mapped = new HashMap<>();

		// For each pattern, from the most precise match to the least one
		// Check the compliance of the given header against the accepted values
		MATCH_HEADERS.stream().forEach(c -> {
			// Headers (K) mapped to input ones (V) for this match level
			final Map<String, String> localMapped = new HashMap<>();
			Arrays.stream(headers).forEach(h -> ACCEPTED_HEADERS.stream().map(a -> a.split(":"))
					.filter(a -> match(c, a, h)).filter(a -> !mapped.containsKey(a[0])).forEach(array -> {
						final String previous = localMapped.put(array[0], h);
						if (previous != null) {
							// Ambiguous header
							throw new ValidationJsonException("csv-file", "ambiguous-header", "header", array[0],
									"name1", previous, "name2", h);
						}
					}));
			// Complete the global set
			mapped.putAll(localMapped);
		});

		// Check the mandatory headers
		CollectionUtils.removeAll(MINIMAL_HEADERS, mapped.keySet()).stream().findFirst().ifPresent(h -> {
			throw new ValidationJsonException("csv-file", "missing-header", "header", h);
		});

		// Return validated header and dropped ones : empty string = ""
		return Arrays.stream(headers).map(MapUtils.invertMap(mapped)::get).map(StringUtils::trimToEmpty)
				.toArray(String[]::new);
	}

	private boolean match(final Function<String[], String> c, final String[] namePattern, final String value) {
		return Pattern.compile("(" + namePattern[0] + "|" + c.apply(namePattern) + ")", Pattern.CASE_INSENSITIVE)
				.matcher(value.trim()).matches();
	}

	/**
	 * Upload a file of quote in add mode.
	 *
	 * @param subscription
	 *            The subscription identifier, will be used to filter the locations from the associated provider.
	 * @param uploadedFile
	 *            Instance entries files to import. Currently support only CSV format.
	 * @param headers
	 *            the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded
	 *            When <code>true</code>, the first line is the headers and the given <code>headers</code> parameter is
	 *            ignored. Otherwise the <code>headers</code> parameter is used.
	 * @param usage
	 *            The optional usage name. When not <code>null</code>, each quote instance will be associated to this
	 *            usage.
	 * @param ramMultiplier
	 *            The multiplier for imported RAM values. Default is 1.
	 * @param encoding
	 *            CSV encoding. Default is UTF-8.
	 * @throws IOException
	 *             When the CSV stream cannot be written.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("{subscription:\\d+}/upload")
	public void upload(@PathParam("subscription") final int subscription,
			@Multipart(value = "csv-file") final InputStream uploadedFile,
			@Multipart(value = "headers", required = false) final String[] headers,
			@Multipart(value = "headers-included", required = false) final boolean headersIncluded,
			@Multipart(value = "usage", required = false) final String usage,
			@Multipart(value = "memoryUnit", required = false) final Integer ramMultiplier,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		subscriptionResource.checkVisible(subscription);
		final String safeEncoding = ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name());

		// Check headers validity
		final String[] headersArray;
		final InputStream fileNoHeader;
		if (headersIncluded) {
			// Header at first line
			final BufferedReader br = new BufferedReader(
					new StringReader(IOUtils.toString(uploadedFile, safeEncoding)));
			headersArray = StringUtils.defaultString(br.readLine(), "").replace(',', ';').split(";");
			fileNoHeader = new ByteArrayInputStream(IOUtils.toByteArray(br, safeEncoding));

		} else {
			// Headers are provided separately
			headersArray = ArrayUtils.isEmpty(headers) ? DEFAULT_HEADERS : headers;
			fileNoHeader = uploadedFile;
		}

		final String[] headersArray2 = checkHeaders(headersArray);
		final String headersString = StringUtils.chop(ArrayUtils.toString(headersArray2)).substring(1).replace(',', ';')
				+ "\n";
		final Reader reader = new InputStreamReader(
				new SequenceInputStream(new ByteArrayInputStream(headersString.getBytes(safeEncoding)), fileNoHeader),
				safeEncoding);

		// Build entries
		csvForBean.toBean(InstanceUpload.class, reader).stream().filter(Objects::nonNull).forEach(i -> {
			try {
				persist(i, subscription, usage, ramMultiplier);
			} catch (final ValidationJsonException e) {
				// Wrap error
				final Map<String, List<Map<String, Serializable>>> errors = e.getErrors();
				new ArrayList<>(errors.keySet()).stream().peek(p -> errors.put("csv-file." + p, errors.get(p)))
						.forEach(errors::remove);
				errors.put("csv-file", List.of(
						Map.of("parameters", (Serializable) Map.of("name", i.getName()), "rule", "csv-invalid-entry")));
				throw e;
			}
		});
	}

	private void persist(final InstanceUpload upload, final int subscription, String usage,
			final Integer ramMultiplier) {
		final QuoteInstanceEditionVo vo = new QuoteInstanceEditionVo();
		vo.setCpu(qResource.round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		vo.setEphemeral(upload.isEphemeral());
		vo.setInternet(upload.getInternet());
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setMaxQuantity(Optional.ofNullable(upload.getMaxQuantity()).filter(q -> q > 0).orElse(null));
		vo.setMinQuantity(upload.getMinQuantity());
		vo.setName(upload.getName());
		vo.setLocation(upload.getLocation());
		vo.setUsage(Optional.ofNullable(upload.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription).getName()).orElse(usage));
		vo.setRam(
				ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		vo.setSubscription(subscription);

		// Find the lowest price
		final ProvInstancePrice instancePrice = qResource.validateLookup("instance",
				qResource.lookup(subscription, vo.getCpu(), vo.getRam(), upload.getConstant(), upload.getOs(),
						upload.getType(), upload.isEphemeral(), upload.getLocation(), upload.getUsage()),
				upload.getName());

		vo.setPrice(instancePrice.getId());
		final UpdatedCost newInstance = qResource.create(vo);
		final int qi = newInstance.getId();

		// Storage part
		final Integer size = Optional.ofNullable(upload.getDisk()).map(Double::intValue).orElse(0);
		if (size > 0) {
			// Size is provided, propagate the upload properties
			final QuoteStorageEditionVo svo = new QuoteStorageEditionVo();
			svo.setName(vo.getName());
			svo.setQuoteInstance(qi);
			svo.setSize(size);
			svo.setLatency(upload.getLatency());
			svo.setInstanceCompatible(true);
			svo.setOptimized(upload.getOptimized());
			svo.setLocation(upload.getLocation());

			// Find the nicest storage
			svo.setType(storageResource
					.lookup(subscription, size, upload.getLatency(), qi, upload.getOptimized(), upload.getLocation())
					.stream().findFirst().orElseThrow(() -> new ValidationJsonException("storage", "NotNull"))
					.getPrice().getType().getName());

			// Default the storage name to the instance name
			svo.setSubscription(subscription);
			storageResource.create(svo);
		}

	}
}
