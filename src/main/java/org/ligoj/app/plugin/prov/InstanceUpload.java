/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.Pattern;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.model.AbstractProvTag;
import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Getter;
import lombok.Setter;

/**
 * CSV data import type
 */
@Getter
@Setter
public class InstanceUpload {

	private static final String SEPARATOR = ",\\s;";
	private static final String FULL_TAG = SEPARATOR + AbstractProvTag.PATTERN + "(:" + AbstractProvTag.PATTERN + ")?";

	private String name;
	private String description;

	/**
	 * Optional requested CPU.
	 */
	@PositiveOrZero
	private double cpu = 0;

	/**
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 * 
	 * @see #cpu
	 */
	@PositiveOrZero
	private Double cpuMax;

	/**
	 * Optional request RAM.
	 */
	@PositiveOrZero
	private double ram = 0;

	/**
	 * The maximal used RAM. When <code>null</code>, the requested RAM is used.
	 * 
	 * @see #ram
	 */
	@PositiveOrZero
	private Double ramMax;

	/**
	 * Optional constant
	 */
	private Boolean constant;
	private VmOs os;

	/**
	 * Optional instance type.
	 */
	private String type;

	/**
	 * Optional location name.
	 */
	private String location;

	/**
	 * Usage name within the target quote. Target usage must exist.
	 */
	private String usage;

	/**
	 * Minimal quantity, when undefined <code>1</code>.
	 */
	@PositiveOrZero
	private int minQuantity = 1;

	/**
	 * Maximal quantity, when undefined <code>1</code>.
	 */
	@PositiveOrZero
	private int maxQuantity = 1;

	/**
	 * Internet access of this VM. By default, private.
	 */
	private InternetAccess internet = InternetAccess.PRIVATE;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance, does not consider the
	 * {@link #maxQuantity} or {@link #minQuantity}. When <code>null</code>, there is no limit. Only relevant for
	 * variable instance price type such as AWS Spot.
	 */
	@PositiveOrZero
	private Double maxVariableCost;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

	/**
	 * Ordered disk sizes.
	 */
	private List<Double> disk = new ArrayList<>();

	/**
	 * Ordered disk max sizes.
	 */
	private List<Double> diskMax = new ArrayList<>();

	/**
	 * Ordered disk latencies.
	 */
	private List<Rate> latency = List.of(Rate.MEDIUM);

	/**
	 * Ordered disk optimizations.
	 */
	private List<ProvStorageOptimized> optimized = new ArrayList<>();

	/**
	 * Optional license model. When <code>null</code>, global's license is used. "INCLUDED" and "BYOL" values are
	 * accepted.
	 */
	private String license;

	/**
	 * Optional built-in software.
	 */
	private String software;

	/**
	 * Optional tags with space, comma or semi-column separator.
	 */
	@Pattern(regexp = "^(" + FULL_TAG + ")*$")
	private String tags;
}
