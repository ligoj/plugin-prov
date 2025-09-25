/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.upload;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.hibernate.Hibernate;
import org.ligoj.app.plugin.prov.*;
import org.ligoj.app.plugin.prov.dao.*;
import org.ligoj.app.plugin.prov.model.*;
import org.ligoj.app.plugin.prov.quote.container.ProvQuoteContainerResource;
import org.ligoj.app.plugin.prov.quote.container.QuoteContainerEditionVo;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

	/**
	 * Default CSV separator.
	 */
	public static final String DEFAULT_SEPARATOR = ";";
	/**
	 * Default CSV encoding.
	 */
	public static final String DEFAULT_ENCODING = StandardCharsets.UTF_8.name();
	private static final List<String> MINIMAL_HEADERS_INSTANCE = List.of("name", "cpu", "ram", "os");
	private static final List<String> MINIMAL_HEADERS_DATABASE = List.of("name", "cpu", "ram", "engine");
	private static final String[] DEFAULT_HEADERS = {"name", "cpu", "ram", "os", "disk", "latency", "optimized",
			"tags"};

	/**
	 * Accepted headers. An array of string having this pattern: <code>name(:pattern)?</code>. The pattern part is optional.
	 */
	private static final List<String> ACCEPTED_HEADERS = List.of("name:(host(name)?|nom)", "cpu:(vcpu|core|process(o|eu)r)s?",
			"gpu:gpu", "ram:memory", "workload:workload", "physical:metal", "os:(system|operating[ -_]?system)",
			"disk:(storage|size|disque|scheibe)", "latency:(disk|storage|disque|scheibe)latency", "optimized:(disk|storage|disque|scheibe)?optimized",
			"resourceType:resourceType",
			"type:(instance|vm)[-_ ]?type", "internet:public", "minQuantity:(min[-_ ]?(quantity)?|quantity[-_ ]?min)",
			"maxQuantity:(max[-_ ]?(quantity)?|quantity[-_ ]?max)", "maxVariableCost:max[-_ ]?(variable)?[-_ ]?cost",
			"ephemeral:preemptive", "location:region", "usage:(use|env|environment)", "budget:(finops|capex|upfront)",
			"optimizer:(greenops|green|carbon|target)", "license:licence", "software:package", "description:note",
			"tags:(tag|label|labels|etiquette|etikett)", "cpuMax:(max[-_ ]?cpu|cpu[-_ ]?max)", "gpuMax:(max[-_ ]?gpu|gpu[-_ ]?max)",
			"ramRate:ramRate", "cpuRate:cpuRate", "gpuRate:gpuRate", "networkRate:networkRate",
			"storageRate:storageRate", "ramMax:(max[-_ ]?(ram|memory)|(ram|memory)[-_ ]?max)",
			"diskMax:(max[-_ ]?(size|disk|storage|disque|scheibe)|(size|disk|storage|disque|scheibe)[-_ ]?max)", "processor:proc", "engine:db",
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
	private ProvBudgetRepository budgetRepository;
	@Autowired
	private ProvOptimizerRepository optimizerRepository;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteContainerResource qcResource;

	@Autowired
	private ProvQuoteDatabaseResource qbResource;

	@Autowired
	private ProvTagResource tagResource;

	@Autowired
	private ProvQuoteInstanceRepository qiRepository;

	@Autowired
	private ProvQuoteContainerRepository qcRepository;

	@Autowired
	private ProvQuoteDatabaseRepository qbRepository;

	// Instance merger
	private final Map<MergeMode, BiFunction<QuoteInstanceEditionVo, UploadContext, Integer>> mergersInstance = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);

	// Container merger
	private final Map<MergeMode, BiFunction<QuoteContainerEditionVo, UploadContext, Integer>> mergersContainer = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);

	// Database merger
	private final Map<MergeMode, BiFunction<QuoteDatabaseEditionVo, UploadContext, Integer>> mergersDatabase = Map
			.of(MergeMode.INSERT, this::modeInsert, MergeMode.KEEP, this::modeKeep, MergeMode.UPDATE, this::modeUpdate);


	private static class UploadContext {
		private Map<String, ProvQuoteInstance> previousQi;
		private Map<String, ProvQuoteContainer> previousQc;
		private Map<String, ProvQuoteDatabase> previousQb;
		private ProvQuote quote;
	}

	/**
	 * Insert mode.
	 */
	private Integer modeInsert(final QuoteInstanceEditionVo vo, final UploadContext context) {
		// Reserve this name
		var qi = new ProvQuoteInstance();
		if (context.previousQi.containsKey(vo.getName())) {
			throw new DataIntegrityViolationException("name");
		}
		context.previousQi.put(vo.getName(), qi);
		qi.setId(qiResource.saveOrUpdate(context.quote, qi, vo).getId());
		vo.setId(qi.getId());
		return qi.getId();
	}

	/**
	 * Insert mode.
	 */
	private Integer modeInsert(final QuoteContainerEditionVo vo, final UploadContext context) {
		// Reserve this name
		var qc = new ProvQuoteContainer();
		if (context.previousQc.containsKey(vo.getName())) {
			throw new DataIntegrityViolationException("name");
		}
		context.previousQc.put(vo.getName(), qc);
		qc.setId(qcResource.saveOrUpdate(context.quote, qc, vo).getId());
		vo.setId(qc.getId());
		return qc.getId();
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
	private Integer modeKeep(final QuoteContainerEditionVo vo, final UploadContext context) {
		nextName(vo, context.previousQc);
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
	private Integer modeUpdate(final QuoteContainerEditionVo vo, final UploadContext context) {
		final var qc = context.previousQc.get(vo.getName());
		if (qc == null) {
			return modeInsert(vo, context);
		}
		// Update the previous entity
		vo.setId(qc.getId());
		qcResource.saveOrUpdate(context.quote, qc, vo);
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
	 * Check the column's name, tying to match to valid headers. All rejected columns are dropped and replaced by an empty
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
				.flatMap(hi -> CollectionUtils.removeAll(MINIMAL_HEADERS_DATABASE, mapped.keySet()).stream()
						.findFirst()).ifPresent(hd -> {
					throw new ValidationJsonException(CSV_FILE, "missing-header", "header", hd);
				});

		// Return validated header and dropped ones : empty string = ""
		return Arrays.stream(headers).map(MapUtils.invertMap(mapped)::get).map(StringUtils::trimToEmpty)
				.toArray(String[]::new);
	}

	private boolean match(final Function<String[], String> c, final String[] namePattern, final String value) {
		return Pattern.compile(c.apply(namePattern), Pattern.CASE_INSENSITIVE).matcher(value.trim()).matches();
	}

	/**
	 * Upload a file of quote.
	 *
	 * @param subscription     The subscription identifier will be used to filter the locations from the associated
	 *                         provider.
	 * @param uploadedFile     Instance entries files to import. Currently, support only CSV format.
	 * @param headers          the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded  When <code>true</code>, the first line is the headers and the given <code>headers</code>
	 *                         parameter is ignored. Otherwise, the <code>headers</code> parameter is used.
	 * @param defaultUsage     The optional usage name. When not <code>null</code>, each quote instance without defined
	 *                         usage will be associated with this usage.
	 * @param defaultBudget    The optional budget name. When not <code>null</code>, each quote instance without defined
	 *                         budget will be associated with this budget.
	 * @param defaultOptimizer The optional optimizer name. When not <code>null</code>, each quote instance without
	 *                         defined usage will be associated with this optimizer.
	 * @param mode             The merge option indicates how the entries are inserted.
	 * @param ramMultiplier    The multiplier for imported RAM values. Default is 1.
	 * @param encoding         CSV encoding. Default is UTF-8.
	 * @param errorContinue    When <code>true</code> errors do not block the upload.
	 * @param createUsage      When <code>true</code>, missing usage are automatically created.
	 * @param createBudget     When <code>true</code>, missing budget are automatically created.
	 * @param createOptimizer  When <code>true</code>, missing optimizer are automatically created.
	 * @param separator        CSV separator. Default is ";".
	 * @throws IOException When the CSV stream cannot be written.
	 */
	@POST
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Path("{subscription:\\d+}/upload")
	public void upload(@PathParam("subscription") final int subscription,
			@Multipart(value = CSV_FILE) final String uploadedFile,
			@Multipart(value = "headers", required = false) final String headers,
			@Multipart(value = "headers-included", required = false) final String headersIncluded,
			@Multipart(value = "usage", required = false) final String defaultUsage,
			@Multipart(value = "budget", required = false) final String defaultBudget,
			@Multipart(value = "optimizer", required = false) final String defaultOptimizer,
			@Multipart(value = "mergeUpload", required = false) final String mode,
			@Multipart(value = "memoryUnit", required = false) final String ramMultiplier,
			@Multipart(value = "errorContinue", required = false) final String errorContinue,
			@Multipart(value = "encoding", required = false) final String encoding,
			@Multipart(value = "createMissingUsage", required = false) final String createUsage,
			@Multipart(value = "createMissingBudget", required = false) final String createBudget,
			@Multipart(value = "createMissingOptimizer", required = false) final String createOptimizer,
			@Multipart(value = "separator", required = false) final String separator) throws IOException {
		upload(subscription,
				uploadedFile,
				StringUtils.trimToEmpty(headers).split(";,"),
				headersIncluded == null || BooleanUtils.toBoolean(headersIncluded),
				defaultUsage,
				defaultBudget,
				defaultOptimizer,
				MergeMode.valueOf(ObjectUtils.getIfNull(mode, MergeMode.KEEP.name())),
				Math.max(1, NumberUtils.toInt(ramMultiplier)),
				BooleanUtils.toBoolean(errorContinue),
				encoding,
				BooleanUtils.toBoolean(createUsage),
				BooleanUtils.toBoolean(createBudget),
				BooleanUtils.toBoolean(createOptimizer),
				separator);
	}

	/**
	 * Upload a file of quote.
	 *
	 * @param subscription     The subscription identifier will be used to filter the locations from the associated
	 *                         provider.
	 * @param uploadedFile     Instance entries files to import. Currently, support only CSV format.
	 * @param headers          the CSV header names. When <code>null</code> or empty, the default headers are used.
	 * @param headersIncluded  When <code>true</code>, the first line is the headers and the given <code>headers</code>
	 *                         parameter is ignored. Otherwise, the <code>headers</code> parameter is used.
	 * @param defaultUsage     The optional usage name. When not <code>null</code>, each quote instance without defined
	 *                         usage will be associated with this usage.
	 * @param defaultBudget    The optional budget name. When not <code>null</code>, each quote instance without defined
	 *                         budget will be associated with this budget.
	 * @param defaultOptimizer The optional optimizer name. When not <code>null</code>, each quote instance without
	 *                         defined usage will be associated with this optimizer.
	 * @param mode             The merge option indicates how the entries are inserted.
	 * @param ramMultiplier    The multiplier for imported RAM values. Default is 1.
	 * @param encoding         CSV encoding. Default is UTF-8.
	 * @param errorContinue    When <code>true</code> errors do not block the upload.
	 * @param createUsage      When <code>true</code>, missing usage are automatically created.
	 * @param createBudget     When <code>true</code>, missing budget are automatically created.
	 * @param createOptimizer  When <code>true</code>, missing optimizer are automatically created.
	 * @param separator        CSV separator. Default is ";".
	 * @throws IOException When the CSV stream cannot be written.
	 */
	public void upload(final int subscription,
			final String uploadedFile,
			final String[] headers,
			final boolean headersIncluded,
			final String defaultUsage,
			final String defaultBudget,
			final String defaultOptimizer,
			final MergeMode mode,
			final Integer ramMultiplier,
			final boolean errorContinue,
			final String encoding,
			final boolean createUsage,
			final boolean createBudget,
			final boolean createOptimizer,
			final String separator) throws IOException {

		log.info("Upload provisioning requested...");
		subscriptionResource.checkVisible(subscription);
		final var quote = resource.getRepository().findBy("subscription.id", subscription);
		final var safeEncoding = ObjectUtils.getIfNull(encoding, DEFAULT_ENCODING);

		// Check headers validity
		final String[] headersArray;
		final InputStream fileNoHeader;
		if (headersIncluded) {
			// Header at first line
			final var br = new BufferedReader(new StringReader(uploadedFile));
			headersArray = Objects.toString(br.readLine()).split(separator);
			fileNoHeader = new ByteArrayInputStream(IOUtils.toByteArray(br, safeEncoding));

		} else {
			// Headers are provided separately
			headersArray = ArrayUtils.isEmpty(headers) ? DEFAULT_HEADERS : headers;
			fileNoHeader = new ByteArrayInputStream(uploadedFile.getBytes(safeEncoding));
		}

		final var headersArray2 = checkHeaders(headersArray);
		final var headersString = StringUtils.chop(ArrayUtils.toString(headersArray2)).substring(1).replace(",",
				separator) + "\n";
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
		final var previousQc = qcRepository.findAll(quote).stream()
				.collect(Collectors.toConcurrentMap(ProvQuoteContainer::getName, Function.identity()));
		final var previousQb = qbRepository.findAll(quote).stream()
				.collect(Collectors.toConcurrentMap(ProvQuoteDatabase::getName, Function.identity()));


		// Initialization for parallel processes
		Hibernate.initialize(quote.getUsages());
		Hibernate.initialize(quote.getBudgets());
		Hibernate.initialize(quote.getOptimizers());
		final var context = new UploadContext();
		context.quote = quote;
		context.previousQi = previousQi;
		context.previousQb = previousQb;
		context.previousQc = previousQc;
		list.stream().filter(Objects::nonNull).filter(i -> i.getName() != null).forEach(i -> {
			try {
				persist(subscription, defaultUsage, defaultBudget, defaultOptimizer, mode, ramMultiplier, list.size(),
						cursor, context, createUsage, createBudget, createOptimizer, i);
			} catch (final ValidationJsonException e) {
				handleUploadError(BooleanUtils.isTrue(errorContinue), handleValidationError(i, e));
			} catch (final ConstraintViolationException e) {
				handleUploadError(BooleanUtils.isTrue(errorContinue), handleValidationError(i, new ValidationJsonException(e)));
			} catch (final RuntimeException e) {
				log.error("Unmanaged error during import of {}", i.getName(), e);
				handleUploadError(BooleanUtils.isTrue(errorContinue), e);
			}
		});
		log.info("Upload provisioning : flushing");
	}

	private void handleUploadError(boolean onErrorContinue, RuntimeException e) {
		if (!onErrorContinue) {
			throw e;
		}
	}

	private <V extends AbstractQuoteVmEditionVo> V copy(final int subscription, final UploadContext context,
			final String defaultUsage, final String defaultBudget, final String defaultOptimizer,
			final int ramMultiplier, final boolean createUsage, final boolean createBudget,
			final boolean createOptimizer, final VmUpload u, final V vo) {
		// Validate the upload object
		vo.setName(u.getName());
		vo.setDescription(u.getDescription());
		vo.setCpu(qiResource.round(ObjectUtils.getIfNull(u.getCpu(), 0d)));
		vo.setGpu(u.getGpu());
		vo.setProcessor(u.getProcessor());
		vo.setLicense(Optional.ofNullable(u.getLicense()).map(StringUtils::upperCase).orElse(null));
		vo.setInternet(u.getInternet());
		vo.setMaxQuantity(Optional.of(u.getMaxQuantity()).filter(q -> q > 0).orElse(null));
		vo.setMinQuantity(u.getMinQuantity());
		vo.setLocation(u.getLocation());
		vo.setCpuRate(u.getCpuRate());
		vo.setGpuRate(u.getGpuRate());
		vo.setRamRate(u.getRamRate());
		vo.setNetworkRate(u.getNetworkRate());
		vo.setStorageRate(u.getStorageRate());
		vo.setWorkload(u.getWorkload());
		vo.setPhysical(u.getPhysical());
		vo.setRam(ramMultiplier * ObjectUtils.getIfNull(u.getRam(), 0).intValue());
		vo.setSubscription(subscription);
		vo.setType(u.getType());
		vo.setCpuMax(u.getCpuMax());
		vo.setGpuMax(u.getGpuMax());
		vo.setRamMax(
				u.getRamMax() == null ? null : ramMultiplier * u.getRamMax().intValue());
		completeUsage(context, defaultUsage, createUsage, u, vo);
		completeBudget(context, defaultBudget, createBudget, u, vo);
		completeOptimizer(context, defaultOptimizer, createOptimizer, u, vo);
		return vo;
	}

	private ValidationJsonException handleValidationError(final VmUpload i, final ValidationJsonException e) {
		final var failedEntry = ObjectUtils.getIfNull(i.getName(), "<unknown>");
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
		copy(upload, vo);
		vo.setSoftware(upload.getSoftware());
		vo.setTenancy(upload.getTenancy());
		return vo;
	}

	private QuoteContainerEditionVo newContainerVo(final VmUpload upload) {
		final var vo = new QuoteContainerEditionVo();
		copy(upload, vo);
		return vo;
	}

	private void copy(final VmUpload upload, final AbstractQuoteInstanceOsEditionVo vo) {
		vo.setMaxVariableCost(upload.getMaxVariableCost());
		vo.setOs(upload.getOs());
	}

	private QuoteDatabaseEditionVo newDatabaseVo(final VmUpload upload) {
		final var vo = new QuoteDatabaseEditionVo();
		vo.setEngine(upload.getEngine());
		vo.setEdition(upload.getEdition());
		return vo;
	}

	private void persist(final int subscription, final String defaultUsage, final String defaultBudget,
			final String defaultOptimizer, final MergeMode mode, final int ramMultiplier, final int size,
			final AtomicInteger cursor, final UploadContext context, final boolean createUsage,
			final boolean createBudget, final boolean createOptimizer, final VmUpload i) {

		if (StringUtils.isNotEmpty(i.getEngine()) || i.getResourceType() == ResourceType.DATABASE) {
			// Database case
			final var merger = mergersDatabase.get(ObjectUtils.getIfNull(mode, MergeMode.KEEP));
			final var vo = copy(subscription, context, defaultUsage, defaultBudget, defaultOptimizer, ramMultiplier,
					createUsage, createBudget, createOptimizer, i, newDatabaseVo(i));
			vo.setPrice(
					qbResource.validateLookup(ResourceType.DATABASE, qbResource.lookup(context.quote, vo), vo.getName()).getId());
			persist(i, subscription, merger, context, vo, QuoteStorageEditionVo::setDatabase, ResourceType.DATABASE);
		} else {
			// Instance/Container case with optionally disks
			if (i.getCpu() > 0) {
				if (i.getResourceType() == ResourceType.CONTAINER) {
					final var vo = copy(subscription, context, defaultUsage, defaultBudget, defaultOptimizer, ramMultiplier,
							createUsage, createBudget, createOptimizer, i, newContainerVo(i));
					final var merger = mergersContainer.get(ObjectUtils.getIfNull(mode, MergeMode.KEEP));
					vo.setPrice(
							qcResource.validateLookup(ResourceType.CONTAINER, qcResource.lookup(context.quote, vo), vo.getName()).getId());
					persist(i, subscription, merger, context, vo, QuoteStorageEditionVo::setContainer, ResourceType.CONTAINER);
				} else {
					final var vo = copy(subscription, context, defaultUsage, defaultBudget, defaultOptimizer, ramMultiplier,
							createUsage, createBudget, createOptimizer, i, newInstanceVo(i));
					final var merger = mergersInstance.get(ObjectUtils.getIfNull(mode, MergeMode.KEEP));
					vo.setPrice(
							qiResource.validateLookup(ResourceType.INSTANCE, qiResource.lookup(context.quote, vo), vo.getName()).getId());
					persist(i, subscription, merger, context, vo, QuoteStorageEditionVo::setInstance, ResourceType.INSTANCE);
				}
			} else if (!i.getDisk().isEmpty()) {
				// Disk only
				final var vo = copy(subscription, context, defaultUsage, defaultBudget, defaultOptimizer, ramMultiplier,
						createUsage, createBudget, createOptimizer, i, newInstanceVo(i));
				persist(i, subscription, null, context, vo, null, null);
			} else {
				log.warn("Ignored entry {}, unable to guess the type", i.getName());
			}
		}

		// Update the cursor
		increment(cursor, size);
	}

	private <V extends AbstractQuoteVmEditionVo> void completeUsage(final UploadContext context,
			final String defaultValue, final boolean create, final VmUpload u, final V vo) {
		// Normalize the name as needed
		vo.setUsage(completeProfile(context, defaultValue, create, u.getUsage(), usageRepository,
				context.quote.getUsages(), name -> {
					final var profile = new ProvUsage();
					profile.setRate(AbstractProvQuoteVmResource.USAGE_DEFAULT.getRate());
					return profile;
				}));
	}

	private <V extends AbstractQuoteVmEditionVo> void completeOptimizer(final UploadContext context, final String defaultValue, final boolean create, final VmUpload u,
			final V vo) {
		// Normalize the name as needed
		vo.setOptimizer(completeProfile(context, defaultValue, create, u.getOptimizer(),
				optimizerRepository, context.quote.getOptimizers(), name -> {
					final var profile = new ProvOptimizer();
					profile.setMode(Optimizer.COST);
					return profile;
				}));
	}

	private <V extends AbstractQuoteVmEditionVo> void completeBudget(final UploadContext context, final String defaultValue, final boolean create, final VmUpload u,
			final V vo) {
		// Normalize the name as needed
		vo.setBudget(completeProfile(context, defaultValue, create, u.getBudget(), budgetRepository,
				context.quote.getBudgets(), name -> new ProvBudget()));
	}

	private <G extends AbstractMultiScoped> String completeProfile(
			final UploadContext context, final String defaultProfile,
			final boolean createProfile, final String uploadName,
			final BaseMultiScopedRepository<G> repository, final List<G> allProfiles,
			final Function<String, G> creator) {
		final var name = Optional.ofNullable(uploadName).orElse(defaultProfile);
		if (name == null) {
			return null;
		}
		var profile = allProfiles.stream().filter(u -> u.getName().equalsIgnoreCase(name)).findFirst().orElse(null);

		// Normalize the usage
		if (profile == null) {
			if (!createProfile) {
				throw new EntityNotFoundException(name);
			}
			// Create the missing usage
			profile = creator.apply(name);
			profile.setName(name);
			profile.setConfiguration(context.quote);
			repository.saveAndFlush(profile);
			allProfiles.add(profile);
		}
		return profile.getName();
	}

	/**
	 * Handle cursor and progress display.
	 */
	private void increment(final AtomicInteger cursor, final int size) {
		final var percent = ((int) (cursor.incrementAndGet() * 100D / size));
		if (cursor.get() > 1 && percent / 10 > ((int) ((cursor.get() - 1) * 100D / size)) / 10) {
			log.info("Upload provisioning : importing {} entries, {}%", size, percent);
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
		final var id = merger == null ? null : merger.apply(vo, context);

		if (merger != null && id == null) {
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

					if (id != null) {
						diskConsumer.accept(svo, id);
					}
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
				}).toList();

		// Tags part
		Arrays.stream(StringUtils.split(ObjectUtils.getIfNull(upload.getTags(), ""), ",;|"))
				.map(StringUtils::trimToNull).filter(Objects::nonNull).forEach(t -> {
					// Instance tags
					final var tag = new TagEditionVo();
					final var parts = StringUtils.splitPreserveAllTokens(t + ":", ':');
					tag.setName(parts[0].trim());
					tag.setValue(StringUtils.trimToNull(parts[1]));

					if (id != null) {
						tag.setResource(id);
						tag.setType(resourceType);
						tagResource.create(subscription, tag);
					}

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
