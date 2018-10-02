/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.PositiveOrZero;

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
	private String name;

	/**
	 * Optional requested CPU.
	 */
	@PositiveOrZero
	private double cpu = 0;

	/**
	 * Optional request RAM.
	 */
	@PositiveOrZero
	private double ram = 0;

	/**
	 * Optional constant
	 */
	private Boolean constant;
	private VmOs os;
	private Double disk;
	private Rate latency = Rate.MEDIUM;
	private ProvStorageOptimized optimized;
	private String type;
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

}
