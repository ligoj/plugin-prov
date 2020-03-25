/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.catalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.dao.NodeRepository;
import org.ligoj.app.model.Node;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvDatabasePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvDatabaseTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstancePriceTermRepository;
import org.ligoj.app.plugin.prov.dao.ProvInstanceTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvLocationRepository;
import org.ligoj.app.plugin.prov.dao.ProvStoragePriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvStorageTypeRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportPriceRepository;
import org.ligoj.app.plugin.prov.dao.ProvSupportTypeRepository;
import org.ligoj.app.plugin.prov.model.AbstractPrice;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

/**
 * Base catalog management with rating.
 */
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
		return Math.round(value * 1000d) / 1000d;
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
		return context.getValidDatabaseType().matcher(type).matches();
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
	 * @param os      The engine to test.
	 * @return <code>true</code> when the configuration enable the given engine.
	 */
	protected boolean isEnabledEngine(final AbstractUpdateContext context, final String engine) {
		return context.getValidDatabaseEngine().matcher(engine.toUpperCase(Locale.ENGLISH)).matches();
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
		if (context.getRegionsMerged().add(region)) {
			final var regionStats = context.getMapRegionToName().getOrDefault(region, new ProvLocation());
			entity.setContinentM49(regionStats.getContinentM49());
			entity.setCountryA2(regionStats.getCountryA2());
			entity.setCountryM49(regionStats.getCountryM49());
			entity.setPlacement(regionStats.getPlacement());
			entity.setRegionM49(regionStats.getRegionM49());
			entity.setSubRegion(regionStats.getSubRegion());
			entity.setLatitude(regionStats.getLatitude());
			entity.setLongitude(regionStats.getLongitude());
			entity.setDescription(regionStats.getName());
			locationRepository.saveAndFlush(entity);
		}
		return entity;
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
	 * @param price     The target entity to update.
	 * @param oldCost    The old cost.
	 * @param newCost    The new cost.
	 * @param updateCost The consumer used to handle the price replacement operation if needed.
	 * @param persister  The consumer used to persist the replacement. Usually a repository operation.
	 * @return The given entity.
	 */
	protected <T extends ProvType, P extends AbstractPrice<T>> P saveAsNeeded(final AbstractUpdateContext context,
			final P price, final double oldCost, final double newCost, final BiConsumer<Double, Double> updateCost,
			final Consumer<P> persister) {
		final var newCostR = round3Decimals(newCost);
		if (context.isForce() || oldCost != newCostR) {
			updateCost.accept(newCostR, newCost);
			persister.accept(price);
		}
		return price;
	}

	/**
	 * Save a price when the attached cost is different from the old one.
	 * 
	 * @param <T>         The price's type.
	 * @param <P>         The instance type's type.
	 * @param context     The context to initialize.
	 * @param entity      The target entity to update.
	 * @param newCost     The new cost.
	 * @param repositorty The repository for persist.
	 */
	protected <T extends ProvType, P extends AbstractTermPrice<T>> void saveAsNeeded(
			final AbstractUpdateContext context, final P entity, final double newCost,
			final RestRepository<P, Integer> repositorty) {
		saveAsNeeded(context, entity, entity.getCost(), newCost, (cR, c) -> {
			entity.setCost(cR);
			entity.setCostPeriod(round3Decimals(c * Math.max(1, entity.getTerm().getPeriod())));
		}, repositorty::save);
	}

	/**
	 * Save a storage price when the attached cost is different from the old one.
	 *
	 * @param context   The context to initialize.
	 * @param entity    The price entity.
	 * @param newCostGb The new GiB cost.
	 * @param persister The consumer used to persist the replacement. Usually a repository operation.
	 */
	protected void saveAsNeeded(final AbstractUpdateContext context, final ProvStoragePrice entity,
			final double newCostGb, final RestRepository<ProvStoragePrice, Integer> repositorty) {
		saveAsNeeded(context, entity, entity.getCostGb(), newCostGb, (cR, c) -> entity.setCostGb(cR),
				repositorty::save);
	}

	/**
	 * Save a price when the attached cost is different from the old one.
	 * 
	 * @param <I>     The price type's type.
	 * @param <P>     The price type.
	 * @param context The context to initialize.
	 * @param entity  The target entity to update.
	 * @param updater The consumer used to persist the replacement. Usually a repository operation.
	 * @return The given entity.
	 */
	protected <P extends Persistable<?>> P copyAsNeeded(final AbstractUpdateContext context, P entity,
			Consumer<P> updater) {
		if (context.isForce() || entity.getId() == null) {
			updater.accept(entity);
		}
		return entity;
	}
}
