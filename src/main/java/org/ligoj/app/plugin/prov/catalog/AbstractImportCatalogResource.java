/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.ObjDoubleConsumer;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceContextType;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.FloatingCost;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.BaseProvQuoteRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvTermPriceRepository;
import org.ligoj.app.plugin.prov.dao.BaseProvTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteDatabaseRepository;
import org.ligoj.app.plugin.prov.dao.ProvQuoteInstanceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportTypeRepository;
import org.ligoj.app.plugin.prov.model.AbstractCodedEntity;
import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractTermPrice;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvType;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.dao.RestRepository;
import org.ligoj.bootstrap.core.dao.csv.CsvForJpa;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Persistable;
import org.springframework.data.repository.CrudRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base catalog management with rating.
 */
@Slf4j
public abstract class AbstractImportCatalogResource {

	protected static final TypeReference<Map<String, String>> MAP_STR = new TypeReference<>() {
		// Nothing to extend
	};

	protected static final TypeReference<Map<String, ProvLocation>> MAP_LOCATION = new TypeReference<>() {
		// Nothing to extend
	};

	protected static final TypeReference<Map<String, Double>> MAP_DOUBLE = new TypeReference<>() {
		// Nothing to extend
	};

	protected static final String BY_NODE = "node";

	/**
	 * Default hours per month.
	 *
	 * @see <a href= "https://en.wikipedia.org/wiki/Gregorian_calendar">Gregorian_calendar</a>
	 */
	public static final int DEFAULT_HOURS_MONTH = 8760 / 12;

	/**
	 * Configuration key used for hours per month. When value is <code>null</code>, use {@link #DEFAULT_HOURS_MONTH}.
	 */
	public static final String CONF_HOURS_MONTH = ProvResource.SERVICE_KEY + ":hours-month";

	@PersistenceContext(type = PersistenceContextType.TRANSACTION, unitName = "pu")
	protected EntityManager em;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected ConfigurationResource configuration;

	@Autowired
	protected NodeRepository nodeRepository;

	@Autowired
	protected ProvLocationRepository locationRepository;

	@Autowired
	protected ProvInstancePriceTermRepository iptRepository;

	@Autowired
	protected ProvInstanceTypeRepository itRepository;

	@Autowired
	protected ProvDatabaseTypeRepository dtRepository;

	@Autowired
	protected ProvInstancePriceRepository ipRepository;

	@Autowired
	protected ProvDatabasePriceRepository dpRepository;

	@Autowired
	protected ProvStoragePriceRepository spRepository;

	@Autowired
	protected ProvStorageTypeRepository stRepository;

	@Autowired
	protected ProvSupportTypeRepository st2Repository;

	@Autowired
	protected ProvSupportPriceRepository sp2Repository;

	@Autowired
	protected ProvQuoteInstanceRepository qiRepository;

	@Autowired
	protected ProvQuoteDatabaseRepository qdRepository;

	@Setter
	@Getter
	@Autowired
	protected ImportCatalogResource importCatalogResource;

	@Autowired
	protected CsvForJpa csvForBean;

	/**
	 * Mapping from instance type name to the rating performance.
	 */
	private final Map<String, Map<String, Rate>> mapRate = new HashMap<>();

	/**
	 * Initialize the given context.
	 *
	 * @param context The context to initialize.
	 * @param node    The provider node identifier.
	 * @param force   When <code>true</code>, all cost attributes are update.
	 * @param <U>     The context type.
	 * @return The context parameter.
	 */
	protected <U extends AbstractUpdateContext> U initContext(final U context, final String node, final boolean force) {
		context.setNode(nodeRepository.findOneExpected(node));
		context.setHoursMonth(configuration.get(CONF_HOURS_MONTH, DEFAULT_HOURS_MONTH));
		context.setForce(force);
		return context;
	}

	/**
	 * Return the most precise rate from a name.
	 *
	 * @param type The rating mapping name.
	 * @param name The name to map.
	 * @return The direct [class, generation, size] rate association, or the [class, generation] rate association, or
	 *         the [class] association, of the explicit "default association or {@link Rate#MEDIUM} value.
	 */
	protected Rate getRate(final String type, final String name) {
		final var map = mapRate.get(type);
		final var fragments = StringUtils.split(StringUtils.defaultString(name, "__"), ".-");
		final var size = fragments[0];
		final var model = StringUtils.rightPad(size, 2, '_').substring(0, 2);
		return Arrays.stream(new String[] { name, size, model, model.substring(0, 1), "default" }).map(map::get)
				.filter(Objects::nonNull).findFirst().orElse(Rate.MEDIUM);
	}

	/**
	 * Read a rate mapping file.
	 *
	 * @param type The target mapping table name to fill.
	 *
	 * @throws IOException When the JSON mapping file cannot be read.
	 */
	protected void initRate(final String type) throws IOException {
		final var mapping = new HashMap<String, Rate>();
		mapRate.put(type, mapping);
		mapping.putAll(objectMapper.readValue(IOUtils
				.toString(new ClassPathResource("rate-" + type + ".json").getInputStream(), StandardCharsets.UTF_8),
				new TypeReference<Map<String, Rate>>() {
					// Nothing to extend
				}));
	}

	/**
	 * Round up to 3 decimals the given value.
	 *
	 * @param value Raw value.
	 * @return The rounded value.
	 */
	protected double round3Decimals(final double value) {
		return FloatingCost.round(value);
	}

	protected <T> Map<String, T> toMap(final String path, final TypeReference<Map<String, T>> type) throws IOException {
		return objectMapper.readValue(
				IOUtils.toString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8), type);
	}

	protected Double toPercent(String raw) {
		if (StringUtils.endsWith(raw, "%")) {
			return Double.valueOf(raw.substring(0, raw.length() - 1));
		}

		// Not a valid percent
		return null;
	}

	/**
	 * Indicate the given instance type is enabled.
	 *
	 * @param context The update context.
	 * @param type    The instance type to test.
	 * @return <code>true</code> when the configuration enable the given instance type.
	 */
	protected boolean isEnabledType(final AbstractUpdateContext context, final String type) {
		return context.getValidInstanceType().matcher(type).matches();
	}

	/**
	 * Indicate the given database type is enabled.
	 *
	 * @param context The update context.
	 * @param type    The database type to test.
	 * @return <code>true</code> when the configuration enable the given database type.
	 */
	protected boolean isEnabledDatabase(final AbstractUpdateContext context, final String type) {
		return type != null && context.getValidDatabaseType().matcher(type).matches();
	}

	/**
	 * Return the OS from it's name.
	 *
	 * @param osName The OS name Case is not sensitive.
	 * @return The OS from it's name. Never <code>null</code>.
	 */
	protected VmOs toVmOs(String osName) {
		return VmOs.valueOf(osName.toUpperCase(Locale.ENGLISH));
	}

	/**
	 * Indicate the given OS is enabled.
	 *
	 * @param context The update context.
	 * @param os      The OS to test.
	 * @return <code>true</code> when the configuration enable the given OS.
	 */
	protected boolean isEnabledOs(final AbstractUpdateContext context, final VmOs os) {
		return isEnabledOs(context, os.name());
	}

	/**
	 * Indicate the given engine is enabled.
	 *
	 * @param context The update context.
	 * @param engine  The engine to test.
	 * @return <code>true</code> when the configuration enable the given engine.
	 */
	protected boolean isEnabledEngine(final AbstractUpdateContext context, final String engine) {
		return context.getValidDatabaseEngine().matcher(engine).matches();
	}

	/**
	 * Indicate the given OS is enabled.
	 *
	 * @param context The update context.
	 * @param os      The OS to test.
	 * @return <code>true</code> when the configuration enable the given OS.
	 */
	protected boolean isEnabledOs(final AbstractUpdateContext context, final String os) {
		return context.getValidOs().matcher(os.toUpperCase(Locale.ENGLISH)).matches();
	}

	/**
	 * Indicate the given region is enabled.
	 *
	 * @param context The update context.
	 * @param region  The region API name to test.
	 * @return <code>true</code> when the configuration enable the given region.
	 */
	protected boolean isEnabledRegion(final AbstractUpdateContext context, final ProvLocation region) {
		return isEnabledRegion(context, region.getName());
	}

	/**
	 * Indicate the given region is enabled.
	 *
	 * @param context The update context.
	 * @param region  The region API name to test.
	 * @return <code>true</code> when the configuration enable the given region.
	 */
	protected boolean isEnabledRegion(final AbstractUpdateContext context, final String region) {
		return context.getValidRegion().matcher(region).matches();
	}

	/**
	 * Install a new region.
	 * 
	 * @param context The update context.
	 * @param region  The region API name to install.
	 * @return The region, created or existing one.
	 */
	protected ProvLocation installRegion(final AbstractUpdateContext context, final String region) {
		final var entity = context.getRegions().computeIfAbsent(region, r -> {
			final var newRegion = new ProvLocation();
			newRegion.setNode(context.getNode());
			newRegion.setName(r);
			return newRegion;
		});

		// Update the location details as needed
		return copyAsNeeded(context, entity, r -> {
			final var regionStats = context.getMapRegionToName().getOrDefault(region, new ProvLocation());
			r.setContinentM49(regionStats.getContinentM49());
			r.setCountryA2(regionStats.getCountryA2());
			r.setCountryM49(regionStats.getCountryM49());
			r.setPlacement(regionStats.getPlacement());
			r.setRegionM49(regionStats.getRegionM49());
			r.setSubRegion(regionStats.getSubRegion());
			r.setLatitude(regionStats.getLatitude());
			r.setLongitude(regionStats.getLongitude());
			r.setDescription(regionStats.getName());
		}, locationRepository);
	}

	/**
	 * Return the {@link ProvLocation} matching the human name.
	 *
	 * @param context   The update context.
	 * @param humanName The required human name.
	 * @return The corresponding {@link ProvLocation} or <code>null</code>.
	 */
	protected ProvLocation getRegionByHumanName(final AbstractUpdateContext context, final String humanName) {
		return context.getRegions().values().stream().filter(r -> isEnabledRegion(context, r))
				.filter(r -> humanName.equals(r.getDescription())).findAny().orElse(null);
	}

	/**
	 * Update the statistics
	 * 
	 * @param context  The update context.
	 * @param location The current region API name.
	 * @param step     The step counter to move forward. May be <code>0</code>.
	 */
	protected void nextStep(final AbstractUpdateContext context, final String location, final int step) {
		nextStep(context.getNode(), location, step);
	}

	/**
	 * Update the current phase for statistics and add 1 to the processed workload.
	 *
	 * @param context The current import context.
	 * @param phase   The new import phase.
	 */
	protected void nextStep(final AbstractUpdateContext context, final String phase) {
		nextStep(context.getNode(), phase);
	}

	/**
	 * Update the current phase for statistics and add 1 to the processed workload.
	 *
	 * @param node  The current import node.
	 * @param phase The new import phase.
	 */
	protected void nextStep(final Node node, final String phase) {
		importCatalogResource.nextStep(node.getId(), t -> {
			importCatalogResource.updateStats(t);
			t.setWorkload(getWorkload(t));
			t.setDone(t.getDone() + 1);
			t.setPhase(phase);
		});
	}

	/**
	 * Update the statistics.
	 * 
	 * @param node     The node provider.
	 * @param location The current region API name.
	 * @param step     The step counter to move forward. May be <code>0</code>.
	 */
	protected void nextStep(final Node node, final String location, final int step) {
		importCatalogResource.nextStep(node.getId(), t -> {
			importCatalogResource.updateStats(t);
			t.setWorkload(getWorkload(t));
			t.setDone(t.getDone() + step);
			t.setLocation(location);
		});
	}

	/**
	 * Return workload corresponding to the given status.
	 *
	 * @param status The current status.
	 * @return Workload corresponding to the given status.
	 */
	protected int getWorkload(ImportCatalogStatus status) {
		return 0;
	}

	/**
	 * Save a price when the attached cost is different from the old one.
	 * 
	 * @param <T>        The price type's type.
	 * @param <P>        The price type.
	 * @param context    The context to initialize.
	 * @param price      The target entity to update.
	 * @param oldCost    The old cost.
	 * @param newCost    The new cost.
	 * @param updateCost The consumer used to handle the price replacement operation if needed.
	 * @param persister  The consumer used to persist the replacement. Usually a repository operation.
	 * @return The given entity.
	 */
	protected <T extends ProvType, P extends AbstractPrice<T>> P saveAsNeeded(final AbstractUpdateContext context,
			final P price, final double oldCost, final double newCost, final ObjDoubleConsumer<Double> updateCost,
			final Consumer<P> persister) {
		final var newCostR = round3Decimals(newCost);
		if (context.isForce() || (price.isNew() && !em.contains(price)) || oldCost != newCostR) {
			updateCost.accept(newCostR, newCost);
			persister.accept(price);
		}
		return price;
	}

	/**
	 * Save a price when the attached cost is different from the old one. The price's code is added to the update codes
	 * set. The cost of the period is also updated accordingly to the attached term.
	 * 
	 * @param <T>         The price's type.
	 * @param <P>         The instance type's type.
	 * @param context     The context to initialize.
	 * @param entity      The target entity to update.
	 * @param newCost     The new cost.
	 * @param repositorty The repository for persist.
	 * @see org.ligoj.app.plugin.prov.catalog.AbstractUpdateContext#getPrices()
	 */
	protected <T extends AbstractInstanceType, P extends AbstractTermPrice<T>> void saveAsNeeded(
			final AbstractUpdateContext context, final P entity, final double newCost,
			final BaseProvTermPriceRepository<T, P> repositorty) {
		context.getPrices().add(entity.getCode());
		saveAsNeeded(context, entity, entity.getCost(), newCost, (cR, c) -> {
			entity.setCost(cR);
			entity.setCostPeriod(round3Decimals(c * Math.max(1, entity.getTerm().getPeriod())));
		}, repositorty::save);
	}

	/**
	 * Save a price when the attached cost is different from the old one. The price's code is added to the update codes
	 * set.
	 * 
	 * @param <T>         The price's type.
	 * @param <P>         The instance type's type.
	 * @param context     The context to initialize.
	 * @param entity      The target entity to update.
	 * @param newCost     The new cost.
	 * @param repositorty The repository used for persist.
	 * @see org.ligoj.app.plugin.prov.catalog.AbstractUpdateContext#getPrices()
	 */
	protected <T extends ProvType, P extends AbstractPrice<T>> void saveAsNeeded(final AbstractUpdateContext context,
			final P entity, final double newCost, final RestRepository<P, Integer> repositorty) {
		context.getPrices().add(entity.getCode());
		saveAsNeeded(context, entity, entity.getCost(), newCost, (cR, c) -> entity.setCost(cR), repositorty::save);
	}

	/**
	 * Save a storage price when the attached cost is different from the old one.
	 *
	 * @param context     The context to initialize.
	 * @param entity      The price entity.
	 * @param newCostGb   The new GiB cost.
	 * @param repositorty The repository used for persist.
	 */
	protected void saveAsNeeded(final AbstractUpdateContext context, final ProvStoragePrice entity,
			final double newCostGb, final RestRepository<ProvStoragePrice, Integer> repositorty) {
		saveAsNeeded(context, entity, entity.getCostGb(), newCostGb, (cR, c) -> entity.setCostGb(cR),
				repositorty::save);
	}

	/**
	 * Save a price when the attached cost is different from the old one.
	 * 
	 * @param <K>     The price type's type.
	 * @param <P>     The price type.
	 * @param context The context to initialize.
	 * @param entity  The target entity to update.
	 * @param updater The consumer used to persist the replacement. Usually a repository operation.
	 * @return The given entity.
	 */
	protected <K extends Serializable, P extends Persistable<K>> P copyAsNeeded(final AbstractUpdateContext context,
			final P entity, final Consumer<P> updater) {
		return copyAsNeeded(context, entity, updater, null);
	}

	/**
	 * Save a type when the corresponding code has not yet been updated in this context.
	 * 
	 * @param <T>        The type's specification.
	 * @param context    The context to initialize.
	 * @param entity     The target entity to update.
	 * @param updater    The consumer used to update the replacement.
	 * @param repository The repository used to persist the replacement. May be <code>null</code>.
	 * @return The given entity.
	 * @see org.ligoj.app.plugin.prov.catalog.AbstractUpdateContext#getMergedTypes()
	 */
	protected <T extends AbstractCodedEntity & ProvType> T copyAsNeeded(final AbstractUpdateContext context,
			final T entity, Consumer<T> updater, final BaseProvTypeRepository<T> repository) {
		if (context.getMergedTypes().add(entity.getCode())) {
			updater.accept(entity);
			if (repository != null) {
				repository.save(entity);
			}
		}
		return entity;
	}

	/**
	 * Save a price when the attached cost is different from the old one.
	 * 
	 * @param <K>        The price type's type.
	 * @param <P>        The price type.
	 * @param context    The context to initialize.
	 * @param entity     The target entity to update.
	 * @param updater    The consumer used to update the replacement.
	 * @param repository The repository used to persist the replacement. May be <code>null</code>.
	 * @return The given entity.
	 */
	protected <K extends Serializable, P extends Persistable<K>> P copyAsNeeded(final AbstractUpdateContext context,
			final P entity, Consumer<P> updater, final RestRepository<P, K> repository) {
		synchronized (entity) {
			if (isNeedUpdate(context, entity)) {
				updater.accept(entity);
				if (repository != null) {
					repository.save(entity);
				}
			}
		}
		return entity;
	}

	private <K extends Serializable, P extends Persistable<K>> boolean isNeedUpdate(final AbstractUpdateContext context,
			final P entity) {
		return context.isForce() || (entity.isNew() && !em.contains(entity));
	}

	/**
	 * Remove the prices that were present in the catalog and not seen in the new catalog with this update.
	 * 
	 * @param context      The update context.
	 * @param storedPrices The whole price context in the database. Some of them have not been seen in the new catalog.
	 * @param pRepository  The price repository used to clean the deprecated and unused prices.
	 * @param qRepository  The quote repository to check for unused prices.
	 * @param <T>          The instance type.
	 * @param <P>          The price type.
	 * @param <Q>          The quote type.
	 */
	protected <T extends AbstractInstanceType, P extends AbstractTermPrice<T>, Q extends AbstractQuoteVm<P>> void purgePrices(
			final AbstractUpdateContext context, final Map<String, P> storedPrices,
			final CrudRepository<P, Integer> pRepository, final BaseProvQuoteRepository<Q> qRepository) {
		final var retiredCodes = new HashSet<>(storedPrices.keySet());
		retiredCodes.removeAll(context.getPrices());
		if (!retiredCodes.isEmpty()) {
			final var nbRetiredCodes = retiredCodes.size();
			retiredCodes.removeAll(qRepository.finUsedPrices(context.getNode().getId()));
			log.info("Purging {} unused of {} retired catalog prices ...", retiredCodes.size(), nbRetiredCodes);
			retiredCodes.stream().map(storedPrices::get).forEach(pRepository::delete);
			log.info("Code purged");
			storedPrices.keySet().removeAll(retiredCodes);
		}
	}
}
