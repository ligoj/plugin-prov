/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

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
	 * Optional license model. <code>null</code> value corresponds to
	 * {@value org.ligoj.app.plugin.prov.model.ProvQuoteInstance#LICENSE_INCLUDED}.
	 */
	private String license;

	/**
	 * Rate applied to required RAM to lookup the suiting instance type. This rate is divided by <code>100</code>, then
	 * multiplied to the required RAM of each memory before calling the lookup. Values lesser than <code>100</code>
	 * allows the lookup to elect an instance having less RAM than the requested one. Value greater than
	 * <code>100</code> makes the lookup to request instance types providing more RAM than the requested one.
	 */
	@Min(1)
	@Max(200)
	@NotNull
	private Integer ramAdjustedRate = 100;

	/**
	 * Optional reservation mode. When <code>null</code>, is {@value ReservationMode#RESERVED}
	 */
	private ReservationMode reservationMode = ReservationMode.RESERVED;
	
	/**
	 * When <code>true</code>, the cost is always refreshed, otherwise, only when at least one pricing dependency is
	 * updated.
	 */
	@JsonIgnore
	private boolean refresh;
}
