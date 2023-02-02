/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ReservationMode;
import org.ligoj.bootstrap.core.DescribedBean;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote definition for edition. Identifier parameter is not used.
 */
@Getter
@Setter
public class QuoteEditionVo extends DescribedBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Default location name when not defined at instance/storage level.
	 */
	@NotNull
	private String location;

	/**
	 * Optional usage name. May be <code>null</code> for a full usage.
	 */
	private String usage;

	/**
	 * Optional budget name. May be <code>null</code> for a budget without initial cost.
	 */
	private String budget;

	/**
	 * Optional optimizer name. May be <code>null</code> for a budget without initial cost.
	 */
	private String optimizer;

	/**
	 * Optional license model. <code>null</code> value corresponds to
	 * {@value org.ligoj.app.plugin.prov.model.ProvQuoteInstance#LICENSE_INCLUDED}.
	 */
	private String license;

	/**
	 * Rate applied to required RAM to find the suiting instance type. This rate is divided by <code>100</code>, then
	 * multiplied to the required RAM of each memory before calling the lookup. Values lesser than <code>100</code>
	 * allows the lookup to elect an instance having less RAM than the requested one. Value greater than
	 * <code>100</code> makes the lookup to request instance types providing more RAM than the requested one.
	 */
	@Min(1)
	@Max(200)
	@NotNull
	private Integer ramAdjustedRate = 100;

	/**
	 * Optional reservation mode. When <code>null</code>, is
	 * {@link org.ligoj.app.plugin.prov.model.ReservationMode#RESERVED}
	 */
	private ReservationMode reservationMode = ReservationMode.RESERVED;

	/**
	 * Optional physical processor. May be <code>null</code>.
	 */
	private String processor;

	/**
	 * Optional physical host requirement. May be <code>null</code>. When <code>true</code>, this instance type is
	 * physical, not virtual.
	 */
	private Boolean physical;

	/**
	 * When <code>true</code>, the cost is always refreshed, otherwise, only when at least one pricing dependency is
	 * updated.
	 */
	@JsonIgnore
	private boolean refresh;

	/**
	 * UI settings. Properties are:
	 * <ul>
	 * <li>Attached tags colors mapping as a JSON map. Key is the tag name. Value is the color code. Color name is not
	 * accepted. Sample: <code>#e4560f</code> or <code>rgb(255, 0, 0)</code>, <code>hsl(0, 100%, 50%)</code>.</li>
	 * </ul>
	 */
	private String uiSettings;

}
