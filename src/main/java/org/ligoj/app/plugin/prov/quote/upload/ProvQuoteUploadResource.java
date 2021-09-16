/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.upload;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
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
import org.hibernate.Hibernate;
import org.ligoj.app.plugin.prov.AbstractQuoteVmEditionVo;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.ProvTagResource;
import org.ligoj.app.plugin.prov.TagEditionVo;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvUsageRepository;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteDatabase;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ResourceType;
import org.ligoj.app.plugin.prov.quote.database.ProvQuoteDatabaseResource;
import org.ligoj.app.plugin.prov.quote.database.QuoteDatabaseEditionVo;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceEditionVo;
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
public class ProvQuoteUploadResource {
	private static final String CSV_FILE = "csv-file";
	private static final List<String> MINIMAL_HEADERS_INSTANCE = List.of("name", "cpu","gpu", "ram", "os");
	private static final List<String> MINIMAL_HEADERS_DATABASE = List.of("name", "cpu","gpu", "ram", "engine");
	private static final String[] DEFAULT_HEADERS = { "name", "cpu","gpu", "ram", "os", "disk", "latency", "optimized",
			"tags" };

	/**
	 * Accepted headers. An array of string having this pattern: <code>name(:pattern)?</code>. Pattern part is optional.
	 */
	private static final List<String> ACCEPTED_HEADERS = List.of("name:host(name)?", "cpu:(vcpu|core|processor)s?",
			"gpu:gpu","ram:memory", "constant:steady", "physical:metal", "os:(system|operating[ -_]?system)",
			"disk:(storage|size)", "latency:(disk|storage)latency", "optimized:(disk|storage)?optimized",
			"type:(instance|vm)[-_ ]?type", "internet:public", "minQuantity:(min[-_ ]?(quantity)?|quantity[-_ ]?min)",
			"maxQuantity:(max[-_ ]?(quantity)?|quantity[-_ ]?max)", "maxVariableCost:max[-_ ]?(variable)?[-_ ]?cost",
			"ephemeral:preemptive", "location:region", "usage:(use|env|environment)", "license:licence",
			"software:package", "description:note", "tags:(tag|label|labels)", "cpuMax:(max[-_ ]?cpu|cpu[-_ ]?max)", 
			"gpuMax:(max[-_ ]?gpu|gpu[-_ ]?max)", "ramRate:ramRate", "cpuRate:cpuRate","gpuRate:gpuRate", "networkRate:networkRate", 
			"storageRate:storageRate","ramMax:(max[-_ ]?(ram|memory)|(ram|memory)[-_ ]?max)",
			"diskMax:(max[-_ ]?(size|disk|storage)|(size|disk|storage)[-_ ]?max)", "processor:proc", "engine:db",
			"edition:version", "tenancy:tenancy");

	/**
	 * Patterns from the most to the least exact match of header.
	 */
	private static final List<Function<String[], String>> LAYER_MATCHERS = List.of(a -> a[0], a -> a[1],
			a -> a[0] + ".*", a -> a[1] + ".*", a -> ".*" + a[0], a -> ".*" + a[1], a -> ".*" + a[0] + ".*",
			a -> ".*" + a[1] + ".*");

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
	private ProvQuoteDatabaseResource qbResource;

	@Autowired
	private ProvTagResource tagResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;
	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	// Instance merger
	private final Map<MergeMode, BiFunction<QuoteInstanceEditionVo, UploadContext, Integer>> mergersInstance = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);

	// Database merger
	private final Map<MergeMode, BiFunction<QuoteDatabaseEditionVo, UploadContext, Integer>> mergersDatabase = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);

	private static class UploadContext {
		private Map<String, ProvQuoteInstance> previousQi;
		private Map<String, ProvQuoteDatabase> previousQb;
		private ProvQuote quote;
	}

	/**
	 * Insert mode.
	 */
	private Integer modeInsert(final QuoteInstanceEditionVo vo, final UploadContext context) {
		// Reserve this name
		var qi = new ProvQuoteInstance();
		context.previousQi.put(vo.getName(), qi);
		qi.setId(qiResource.saveOrUpdate(context.quote, qi, vo).getId());
		vo.setId(qi.getId());
		return qi.getId();
	}

	/**
	 * Insert mode.
	 */
	private Integer modeInsert(final QuoteDatabaseEditionVo vo, final UploadContext context) {
		// Reserve this name
		var qb = new ProvQuoteDatabase();
		context.previousQb.put(vo.getName(), qb);
		qb.setId(qbResource.saveOrUpdate(context.quote, qb, vo).getId());
		vo.setId(qb.getId());
		return qb.getId();
	}

	private <Q extends AbstractQuoteVmEditionVo> void nextName(final Q vo, final Map<String, ?> previous) {
		var name = vo.getName();
		var counter = 0;
		while (previous.containsKey(name)) {
			name = vo.getName() + " " + ++counter;
		}
		// Fix the instance's name in order to be unique during this upload.
		vo.setName(name);
	}

	/**
	 * Keep mode.
	 */
	private Integer modeKeep(final QuoteInstanceEditionVo vo, final UploadContext context) {
		nextName(vo, context.previousQi);
		return modeInsert(vo, context);
	}

	/**
	 * Keep mode.
	 */
	private Integer modeKeep(final QuoteDatabaseEditionVo vo, final UploadContext context) {
		nextName(vo, context.previousQb);
		return modeInsert(vo, context);
	}

	/**
	 * Update mode.
	 */
	private Integer modeUpdate(final QuoteInstanceEditionVo vo, final UploadContext context) {
		final var qi = context.previousQi.get(vo.getName());
		if (qi == null) {
			return modeInsert(vo, context);
		}
		// Update the previous entity
		vo.setId(qi.getId());
		qiResource.saveOrUpdate(context.quote, qi, vo);
		return null;
	}

	/**
	 * Update mode.
	 */
	private Integer modeUpdate(final QuoteDatabaseEditionVo vo, final UploadContext context) {
		final var qi = context.previousQb.get(vo.getName());
		if (qi == null) {
			return modeInsert(vo, context);
		}
		// Update the previous entity
		vo.setId(qi.getId());
		qbResource.saveOrUpdate(context.quote, qi, vo);
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
		final var mapped = new HashMap<String, String>();
		final var mappedUser = new HashSet<String>();

		// For each pattern, from the most precise match to the least one
		// Check the compliance of the given header against the accepted values
		LAYER_MATCHERS.forEach(layer -> {
			// Headers (K) mapped to input ones (V) for this match layer
			final var layerMapped = new HashMap<String, String>();
			Arrays.stream(headers)
					.forEach(h -> ACCEPTED_HEADERS.stream().map(mapping -> mapping.split(":"))
							.filter(mapping -> !mapped.containsKey(mapping[0]) && !mappedUser.contains(h))
							.filter(mapping -> match(layer, mapping, cleanHeader(h))).forEach(patterns -> {
								final var previous = layerMapped.put(patterns[0], h);
								if (previous != null) {
									// Ambiguous header
									throw new ValidationJsonException(CSV_FILE, "ambiguous-header", "header",
											patterns[0], "name1", previous, "name2", h);
								}
							}));
			// Complete the global set
			mapped.putAll(layerMapped);
			mappedUser.addAll(layerMapped.values());
		});

		// Check the mandatory headers
		CollectionUtils.removeAll(MINIMAL_HEADERS_INSTANCE, mapped.keySet()).stream().findFirst()
				.ifPresent(hi -> CollectionUtils.removeAll(MINIMAL_HEADERS_DATABASE, mapped.keySet()).stream()
						.findFirst().ifPresent(hd -> {
							throw new ValidationJsonException(CSV_FILE, "missing-header", "header", hd);
						}));

		// Return validated header and dropped ones : empty string = ""
		return Arrays.stream(headers).map(MapUtils.invertMap(mapped)::get).map(StringUtils::trimToEmpty)
				.toArray(String[]::new);
	}

	private boolean match(final Function<String[], String> c, final String[] namePattern, final String value) {
		return Pattern.compile(c.apply(namePattern), Pattern.CASE_INSENSITIVE).matcher(value.trim()).matches();
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
	public void upload(final int subscription, final InputStream uploadedFile, final String[] headers,
			final boolean headersIncluded, final String usage, final Integer ramMultiplier, final String encoding)
			throws IOException {
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
		final var quote = resource.getRepository().findBy("subscription.id", subscription);
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
		final var list = csvForBean.toBean(VmUpload.class, reader);
		log.info("Upload provisioning : importing {} entries", list.size());
		final var cursor = new AtomicInteger(0);
		final var previousQi = qiRepository.findAll(quote).stream()
				.collect(Collectors.toConcurrentMap(ProvQuoteInstance::getName, Function.identity()));
		final var previousQb = qbRepository.findAll(quote).stream()
				.collect(Collectors.toConcurrentMap(ProvQuoteDatabase::getName, Function.identity()));

		// Initialization for parallel process
		Hibernate.initialize(quote.getUsages());
		Hibernate.initialize(quote.getBudgets());
		final var context = new UploadContext();
		context.quote = quote;
		context.previousQi = previousQi;
		context.previousQb = previousQb;
		list.stream().filter(Objects::nonNull).filter(i -> i.getName() != null).forEach(i -> {
			try {
				persist(subscription, usage, mode, ramMultiplier, list, cursor, context, i);
			} catch (final ValidationJsonException e) {
				throw handleValidationError(i, e);
			} catch (final ConstraintViolationException e) {
				throw handleValidationError(i, new ValidationJsonException(e));
			} catch (final Throwable e) {
				log.error("Unmanaged error during import of " + i.getName(), e);
				throw e;
			}
		});
		log.info("Upload provisioning : flushing");
	}

	private <V extends AbstractQuoteVmEditionVo> V copy(final VmUpload upload, final int subscription,
			final String usage, final Integer ramMultiplier, final V vo) {
		// Validate the upload object
		vo.setName(upload.getName());
		vo.setDescription(upload.getDescription());
		vo.setCpu(qiResource.round(ObjectUtils.defaultIfNull(upload.getCpu(), 0d)));
		//vo.setGpu() 
		vo.setProcessor(upload.getProcessor());
		vo.setLicense(Optional.ofNullable(upload.getLicense()).map(StringUtils::upperCase).orElse(null));
		vo.setInternet(upload.getInternet());
		vo.setMaxQuantity(Optional.ofNullable(upload.getMaxQuantity()).filter(q -> q > 0).orElse(null));
		vo.setMinQuantity(upload.getMinQuantity());
		vo.setLocation(upload.getLocation());
		vo.setCpuRate(upload.getCpuRate());
		//vo.setGpuRate(upload.getGpuRate());
		vo.setRamRate(upload.getRamRate());
		vo.setNetworkRate(upload.getNetworkRate());
		vo.setStorageRate(upload.getStorageRate());
		vo.setConstant(upload.getConstant());
		vo.setPhysical(upload.getPhysical());
		vo.setUsage(Optional.ofNullable(upload.getUsage())
				.map(u -> resource.findConfiguredByName(usageRepository, u, subscription).getName()).orElse(usage));
		vo.setRam(
				ObjectUtils.defaultIfNull(ramMultiplier, 1) * ObjectUtils.defaultIfNull(upload.getRam(), 0).intValue());
		vo.setSubscription(subscription);
		vo.setType(upload.getType());
		vo.setCpuMax(upload.getCpuMax());
		vo.setRamMax(upload.getRamMax() == null ? null
				: ObjectUtils.defaultIfNull(ramMultiplier, 1) * upload.getRamMax().intValue());
		return vo;
	}

	private ValidationJsonException handleValidationError(final VmUpload i, final ValidationJsonException e) {
		final var failedEntry = ObjectUtils.defaultIfNull(i.getName(), "<unknown>");
		log.info("Upload provisioning failed for entry {}", failedEntry, e);
		final var errors = e.getErrors();
		new ArrayList<>(errors.keySet()).stream().peek(p -> errors.put("csv-file." + p, errors.get(p)))
				.forEach(errors::remove);
		errors.put(CSV_FILE,
				List.of(Map.of("parameters", (Serializable) Map.of("name", failedEntry), "rule", "csv-invalid-entry")));
		return e;
	}

	private QuoteInstanceEditionVo newInstanceVo(final VmUpload upload) {
		final var vo = new QuoteInstanceEditionVo();
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setOs(upload.getOs());
		vo.setLicense(Optional.ofNullable(upload.getLicense()).map(StringUtils::upperCase).orElse(null));
		vo.setSoftware(upload.getSoftware());
		vo.setTenancy(upload.getTenancy());
		vo.setEphemeral(upload.isEphemeral());
		return vo;
	}

	private QuoteDatabaseEditionVo newDatabaseVo(final VmUpload upload) {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setEngine(upload.getEngine());
		vo.setEdition(upload.getEdition());
		return vo;
	}

	private void persist(final int subscription, final String usage, final MergeMode mode, final Integer ramMultiplier,
			final List<VmUpload> list, final AtomicInteger cursor, final UploadContext context, VmUpload i) {
		if (StringUtils.isNotEmpty(i.getEngine())) {
			// Database case
			final var merger = mergersDatabase.get(ObjectUtils.defaultIfNull(mode, MergeMode.KEEP));
			final var vo = copy(i, subscription, usage, ramMultiplier, newDatabaseVo(i));
			vo.setPrice(
					qbResource.validateLookup("database", qbResource.lookup(context.quote, vo), vo.getName()).getId());
			persist(i, subscription, merger, context, vo, QuoteStorageEditionVo::setDatabase, ResourceType.DATABASE);
		} else {
			// Instance/Container case
			final var merger = mergersInstance.get(ObjectUtils.defaultIfNull(mode, MergeMode.KEEP));
			final var vo = copy(i, subscription, usage, ramMultiplier, newInstanceVo(i));
			vo.setPrice(
					qiResource.validateLookup("instance", qiResource.lookup(context.quote, vo), vo.getName()).getId());
			persist(i, subscription, merger, context, vo, QuoteStorageEditionVo::setInstance, ResourceType.INSTANCE);
		}
		final var percent = ((int) (cursor.incrementAndGet() * 100D / list.size()));
		if (cursor.get() > 1 && percent / 10 > ((int) ((cursor.get() - 1) * 100D / list.size())) / 10) {
			log.info("Upload provisioning : importing {} entries, {}%", list.size(), percent);
		}
	}

	/**
	 * Validate the input object, do a lookup, then create the {@link ProvQuoteInstance} and the
	 * {@link ProvQuoteStorage} entities.
	 */
	private <V extends AbstractQuoteVmEditionVo> void persist(final VmUpload upload, final int subscription,
			final BiFunction<V, UploadContext, Integer> merger, final UploadContext context, final V vo,
			final ObjIntConsumer<QuoteStorageEditionVo> diskConsumer, final ResourceType resourceType) {

		// Create the quote instance from the validated inputs
		final var id = merger.apply(vo, context);

		if (id == null) {
			// Do not continue
			return;
		}

		// Storage part
		final var disks = IntStream.range(0, upload.getDisk().size()).filter(index -> upload.getDisk().get(index) > 0)
				.mapToObj(index -> {
					final var size = upload.getDisk().get(index).intValue();
					final var sizeMax = upload.getDiskMax().size() > index ? upload.getDiskMax().get(index).intValue()
							: null;
					// Size is provided, propagate the upload properties
					final var svo = new QuoteStorageEditionVo();
					svo.setName(vo.getName() + (index == 0 ? "" : index));
					diskConsumer.accept(svo, id);
					svo.setSize(size);
					svo.setSizeMax(sizeMax);
					svo.setLatency(getItem(upload.getLatency(), index));
					svo.setOptimized(getItem(upload.getOptimized(), index));

					// Find the nicest storage
					svo.setType(storageResource.lookup(context.quote, svo).stream().findFirst()
							.orElseThrow(() -> new ValidationJsonException("storage", "NotNull")).getPrice().getType()
							.getCode());

					// Default the storage name to the instance name
					svo.setSubscription(subscription);
					return storageResource.create(svo).getId();
				}).collect(Collectors.toList());

		// Tags part
		Arrays.stream(StringUtils.split(ObjectUtils.defaultIfNull(upload.getTags(), ""), ",;"))
				.map(StringUtils::trimToNull).filter(Objects::nonNull).forEach(t -> {
					// Instance tags
					final var tag = new TagEditionVo();
					final var parts = StringUtils.splitPreserveAllTokens(t + ":", ':');
					tag.setName(parts[0]);
					tag.setValue(StringUtils.trimToNull(parts[1]));
					tag.setResource(id);
					tag.setType(resourceType);
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
