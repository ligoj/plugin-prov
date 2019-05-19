/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.instance;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import org.ligoj.app.plugin.prov.InstanceUpload;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.ProvTagResource;
import org.ligoj.app.plugin.prov.TagEditionVo;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.storage.ProvQuoteStorageResource;
import org.ligoj.app.plugin.prov.quote.storage.QuoteStorageEditionVo;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.csv.CsvForBean;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * The instance part of the provisioning from upload CSV file.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
@Slf4j
public class ProvQuoteInstanceUploadResource {
	private static final String CSV_FILE = "csv-file";
	private static final List<String> MINIMAL_HEADERS = List.of("name", "cpu", "ram", "os");
	private static final String[] DEFAULT_HEADERS = { "name", "cpu", "ram", "os", "disk", "latency", "optimized",
			"tags" };

	/**
	 * Accepted headers. An array of string having this pattern: <code>name(:pattern)?</code>. Pattern part is optional.
	 */
	private static final List<String> ACCEPTED_HEADERS = List.of("name", "cpu:(vcpu|core|processor)s?", "ram:memory",
			"constant", "os:(system|operating system)", "disk:size", "latency", "optimized:(disk)?optimized",
			"type:instancetype", "internet", "minQuantity:min", "maxQuantity:max", "maxVariableCost:maxcost",
			"ephemeral:preemptive", "location:region", "usage:(use|env|environment)", "license", "software",
			"description:note", "tags:(tag|label|labels)");

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
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvTagResource tagResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	private final Map<MergeMode, BiFunction<QuoteInstanceEditionVo, Map<String, ProvQuoteInstance>, Integer>> mergers = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);

	/**
	 * Insert mode.
	 */
	private Integer modeInsert(final QuoteInstanceEditionVo vo, final Map<String, ProvQuoteInstance> previous) {
		// Reserve this name
		var qi = new ProvQuoteInstance();
		previous.put(vo.getName(), qi);
		qi.setId(qiResource.create(vo).getId());
		vo.setId(qi.getId());
		return qi.getId();
	}

	/**
	 * Keep mode.
	 */
	private Integer modeKeep(final QuoteInstanceEditionVo vo, final Map<String, ProvQuoteInstance> previous) {
		// Fix the instance's name in order to be unique during this upload.
		var name = vo.getName();
		var counter = 0;
		while (previous.containsKey(name)) {
			name = vo.getName() + " " + ++counter;
		}
		vo.setName(name);
		return modeInsert(vo, previous);
	}

	/**
	 * Update mode.
	 */
	private Integer modeUpdate(final QuoteInstanceEditionVo vo, final Map<String, ProvQuoteInstance> previous) {
		final var qi = previous.get(vo.getName());
		if (qi == null) {
			return modeInsert(vo, previous);
		}
		// Update the previous entity
		vo.setId(qi.getId());
		qiResource.update(vo);
		return null;
	}

	private String cleanHeader(final String header) {
		return StringUtils.unwrap(header, '\"').trim();
	}

	/**
	 * Check column's name, tying to match to valid headers. All rejected columns are dropped and replaced by an empty
	 * string <code>""</code>.
	 *
	 * @param headers The given headers.
	 * @return The mapped and valid columns. Some may be empty and would be dropped.
	 */
	private String[] checkHeaders(final String... headers) {
		// Headers (K) mapped to input ones (V)
		final Map<String, String> mapped = new HashMap<>();

		// For each pattern, from the most precise match to the least one
		// Check the compliance of the given header against the accepted values
		MATCH_HEADERS.forEach(c -> {
			// Headers (K) mapped to input ones (V) for this match level
			final Map<String, String> localMapped = new HashMap<>();
			Arrays.stream(headers).forEach(h -> ACCEPTED_HEADERS.stream().map(a -> a.split(":"))
					.filter(a -> match(c, a, cleanHeader(h))).filter(a -> !mapped.containsKey(a[0])).forEach(array -> {
						final var previous = localMapped.put(array[0], h);
						if (previous != null) {
							// Ambiguous header
							throw new ValidationJsonException(CSV_FILE, "ambiguous-header", "header", array[0], "name1",
									previous, "name2", h);
						}
					}));
			// Complete the global set
			mapped.putAll(localMapped);
		});

		// Check the mandatory headers
		CollectionUtils.removeAll(MINIMAL_HEADERS, mapped.keySet()).stream().findFirst().ifPresent(h -> {
			throw new ValidationJsonException(CSV_FILE, "missing-header", "header", h);
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
	 * @param subscription    The subscription identifier, will be used to filter the locations from the associated
	 *                        provider.
	 * @param uploadedFile    Instance entries files to import. Currently support only CSV format.
	 * @param headers         the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded When <code>true</code>, the first line is the headers and the given <code>headers</code>
	 *                        parameter is ignored. Otherwise the <code>headers</code> parameter is used.
	 * @param usage           The optional usage name. When not <code>null</code>, each quote instance will be
	 *                        associated to this usage.
	 * @param ramMultiplier   The multiplier for imported RAM values. Default is 1.
	 * @param encoding        CSV encoding. Default is UTF-8.
	 * @throws IOException When the CSV stream cannot be written.
	 */
	public void upload(@PathParam("subscription") final int subscription, final InputStream uploadedFile,
			final String[] headers, final boolean headersIncluded, final String usage, final Integer ramMultiplier,
			final String encoding) throws IOException {
		upload(subscription, uploadedFile, headers, headersIncluded, usage, MergeMode.KEEP, ramMultiplier, encoding);
	}

	/**
	 * Upload a file of quote in add mode.
	 *
	 * @param subscription    The subscription identifier, will be used to filter the locations from the associated
	 *                        provider.
	 * @param uploadedFile    Instance entries files to import. Currently support only CSV format.
	 * @param headers         the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded When <code>true</code>, the first line is the headers and the given <code>headers</code>
	 *                        parameter is ignored. Otherwise the <code>headers</code> parameter is used.
	 * @param usage           The optional usage name. When not <code>null</code>, each quote instance will be
	 *                        associated to this usage.
	 * @param mode            The merge option indicates how the entries are inserted.
	 * @param ramMultiplier   The multiplier for imported RAM values. Default is 1.
	 * @param encoding        CSV encoding. Default is UTF-8.
	 * @throws IOException When the CSV stream cannot be written.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("{subscription:\\d+}/upload")
	public void upload(@PathParam("subscription") final int subscription,
			@Multipart(value = CSV_FILE) final InputStream uploadedFile,
			@Multipart(value = "headers", required = false) final String[] headers,
			@Multipart(value = "headers-included", required = false) final boolean headersIncluded,
			@Multipart(value = "usage", required = false) final String usage,
			@Multipart(value = "mergeUpload", required = false) final MergeMode mode,
			@Multipart(value = "memoryUnit", required = false) final Integer ramMultiplier,
			@Multipart(value = "encoding", required = false) final String encoding) throws IOException {
		log.info("Upload provisioning requested...");
		subscriptionResource.checkVisible(subscription);
		final var safeEncoding = ObjectUtils.defaultIfNull(encoding, StandardCharsets.UTF_8.name());

		// Check headers validity
		final String[] headersArray;
		final InputStream fileNoHeader;
		if (headersIncluded) {
			// Header at first line
			final var br = new BufferedReader(new StringReader(IOUtils.toString(uploadedFile, safeEncoding)));
			headersArray = StringUtils.defaultString(br.readLine(), "").replace(',', ';').split(";");
			fileNoHeader = new ByteArrayInputStream(IOUtils.toByteArray(br, safeEncoding));

		} else {
			// Headers are provided separately
			headersArray = ArrayUtils.isEmpty(headers) ? DEFAULT_HEADERS : headers;
			fileNoHeader = uploadedFile;
		}

		final var headersArray2 = checkHeaders(headersArray);
		final var headersString = StringUtils.chop(ArrayUtils.toString(headersArray2)).substring(1).replace(',', ';')
				+ "\n";
		final var reader = new InputStreamReader(
				new SequenceInputStream(new ByteArrayInputStream(headersString.getBytes(safeEncoding)), fileNoHeader),
				safeEncoding);

		// Build entries
		log.info("Upload provisioning : reading, using header {}", headersString);
		final var list = csvForBean.toBean(InstanceUpload.class, reader);
		log.info("Upload provisioning : importing {} entries", list.size());
		final var cursor = new AtomicInteger(0);
		final var previous = qiRepository.findAll(subscription).stream()
				.collect(Collectors.toMap(ProvQuoteInstance::getName, Function.identity()));
		final var merger = mergers.get(ObjectUtils.defaultIfNull(mode, MergeMode.KEEP));
		list.stream().filter(Objects::nonNull).forEach(i -> {
			try {
				persist(i, subscription, usage, ramMultiplier, merger, previous);
				final var percent = ((int) (cursor.incrementAndGet() * 100D / list.size()));
				if (cursor.get() > 1 && percent / 10 > ((int) ((cursor.get() - 1) * 100D / list.size())) / 10) {
					log.info("Upload provisioning : importing {} entries, {}%", list.size(), percent);
				}
			} catch (final ValidationJsonException e) {
				// Wrap error
				log.info("Upload provisioning : failed", e);
				final var errors = e.getErrors();
				new ArrayList<>(errors.keySet()).stream().peek(p -> errors.put("csv-file." + p, errors.get(p)))
						.forEach(errors::remove);
				errors.put(CSV_FILE, List.of(
						Map.of("parameters", (Serializable) Map.of("name", i.getName()), "rule", "csv-invalid-entry")));
				throw e;
			}
		});
		log.info("Upload provisioning : flushing");
	}

	/**
	 * Validate the input object, do a lookup, then create the {@link ProvQuoteInstance} and the
	 * {@link ProvQuoteStorage} entities.
	 */
	private void persist(final InstanceUpload upload, final int subscription, String usage, final Integer ramMultiplier,
			final BiFunction<QuoteInstanceEditionVo, Map<String, ProvQuoteInstance>, Integer> merger,
			final Map<String, ProvQuoteInstance> previous) {
		// Validate the upload object
		final var vo = new QuoteInstanceEditionVo();
		vo.setName(upload.getName());
		vo.setDescription(upload.getDescription());
		vo.setCpu(qiResource.round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		vo.setEphemeral(upload.isEphemeral());
		vo.setInternet(upload.getInternet());
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setMaxQuantity(Optional.ofNullable(upload.getMaxQuantity()).filter(q -> q > 0).orElse(null));
		vo.setMinQuantity(upload.getMinQuantity());
		vo.setLocation(upload.getLocation());
		vo.setOs(upload.getOs());
		vo.setLicense(Optional.ofNullable(upload.getLicense()).map(StringUtils::upperCase).orElse(null));
		vo.setSoftware(upload.getSoftware());
		vo.setConstant(upload.getConstant());
		vo.setUsage(Optional.ofNullable(upload.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription).getName()).orElse(usage));
		vo.setRam(
				ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		vo.setSubscription(subscription);
		vo.setType(upload.getType());

		// Find the lowest price
		vo.setPrice(qiResource.validateLookup("instance", qiResource.lookupInternal(subscription, vo), vo.getName())
				.getId());

		// Create the quote instance from the validated inputs
		final var id = merger.apply(vo, previous);

		if (id == null) {
			// Do not continue
			return;
		}

		// Storage part
		final var disks = IntStream.range(0, upload.getDisk().size()).filter(index -> upload.getDisk().get(index) > 0)
				.mapToObj(index -> {
					final var size = upload.getDisk().get(index).intValue();
					// Size is provided, propagate the upload properties
					final var svo = new QuoteStorageEditionVo();
					svo.setName(vo.getName() + (index == 0 ? "" : index));
					svo.setQuoteInstance(id);
					svo.setSize(size);
					svo.setLatency(getItem(upload.getLatency(), index));
					svo.setOptimized(getItem(upload.getOptimized(), index));

					// Find the nicest storage
					svo.setType(storageResource.lookup(subscription, svo).stream().findFirst()
							.orElseThrow(() -> new ValidationJsonException("storage", "NotNull")).getPrice().getType()
							.getName());

					// Default the storage name to the instance name
					svo.setSubscription(subscription);
					return storageResource.create(svo).getId();
				}).collect(Collectors.toList());

		// Tags part
		Arrays.stream(StringUtils.split(ObjectUtils.defaultIfNull(upload.getTags(), ""), " ,;"))
				.map(StringUtils::trimToNull).filter(Objects::nonNull).forEach(t -> {
					// Instance tags
					final var tag = new TagEditionVo();
					final var parts = StringUtils.splitPreserveAllTokens(t + ":", ':');
					tag.setName(parts[0]);
					tag.setValue(StringUtils.trimToNull(parts[1]));
					tag.setResource(id);
					tag.setType(ResourceType.INSTANCE);
					tagResource.create(subscription, tag);

					// Storage tags
					tag.setType(ResourceType.STORAGE);
					disks.forEach(d -> {
						tag.setResource(d);
						tagResource.create(subscription, tag);
					});
				});
	}

	private <T> T getItem(final List<T> items, final int index) {
		if (items.isEmpty()) {
			return null;
		}
		return items.get(Math.min(items.size() - 1, index));
	}
}
