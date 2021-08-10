/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.quote.support.QuoteTagSupport;
import org.ligoj.bootstrap.core.DescribedBean;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for an instance while editing it.
 */
@Getter
@Setter
public abstract class AbstractQuoteVmEditionVo extends DescribedBean<Integer> implements QuoteTagSupport {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Instance price configuration matching to the requirements.
	 */
	@NotNull
	@Positive
	private Integer price;

	/**
	 * Related subscription identifier.
	 */
	@NotNull
	@Positive
	private Integer subscription;

	/**
	 * The requested CPU
	 */
	@NotNull
	@Positive
	private double cpu;

	/**
	 * The maximal used CPU. When <code>null</code>, the requested CPU is used.
	 *
	 * @see #cpu
	 */
	@PositiveOrZero
	private Double cpuMax;

	/**
	 * The requested memory in MiB.
	 */
	@NotNull
	@Positive
	private int ram;

	/**
	 * The maximal used RAM in MiB. When <code>null</code>, the requested RAM is used.
	 *
	 * @see #ram
	 */
	@PositiveOrZero
	private Integer ramMax;

	/**
	 * The optional requested CPU behavior. When <code>false</code>, the CPU is variable, with boost mode.
	 */
	private Boolean constant;

	/**
	 * Optional physical processor. Case insensitive with 'like' match.
	 */
	private String processor;

	/**
	 * When <code>true</code>, this instance type is physical, not virtual.
	 */
	private Boolean physical;

	/**
	 * The Internet access : Internet facing, etc.
	 */
	@NotNull
	private InternetAccess internet = InternetAccess.PRIVATE;

	/**
	 * The minimal quantity of this instance.
	 */
	@PositiveOrZero
	@NotNull
	private Integer minQuantity = 1;

	/**
	 * The maximal quantity of this instance. When defined, must be greater than {@link #minQuantity}
	 */
	@PositiveOrZero
	private Integer maxQuantity;

	/**
	 * Optional required location name. When <code>null</code>, the default quote's one will be used.
	 */
	private String location;

	/**
	 * Optional applied usage name. When <code>null</code>, the default quote's one will be used.
	 */
	private String usage;

	/**
	 * Optional applied budget name. When <code>null</code>, the default quote's one will be used.
	 */
	private String budget;

	/**
	 * Optional license model. When <code>null</code>, global's configuration is used. "BYOL" and "INCLUDED" are
	 * accepted.
	 */
	private String license;

	/**
	 * Optional required type's code. Will be erased on refresh.
	 */
	private String type;

	/**
	 * The tags to override when not <code>null</code>.
	 */
	private List<TagVo> tags;

	/**
	 * The instance could be terminated by the provider. Default <code>false</code>.
	 */
	private boolean ephemeral;

	/**
	 * Optional auto-scaling capability requirement.
	 */
	private boolean autoScale;

	/**
	 * Optional CPU rate requirement.
	 */
	private Rate cpuRate;

	/**
	 * Optional network rate requirement.
	 */
	private Rate networkRate;

	/**
	 * Optional storage rate requirement.
	 */
	private Rate storageRate;

	/**
	 * Optional RAM rate requirement.
	 */
	private Rate ramRate;

	@JsonIgnore
	public String getLocationName() {
		return getLocation();
	}

	@JsonIgnore
	public String getUsageName() {
		return getUsage();
	}

	@JsonIgnore
	public String getBudgetName() {
		return getBudget();
	}
}
