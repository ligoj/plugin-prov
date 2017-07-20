package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QuoteInstanceEditionVo extends DescribedBean<Integer> {

	/**
	 * Instance price configuration matching to the requirements.
	 */
	@NotNull
	private Integer instancePrice;

	/**
	 * Related subscription identifier.
	 */
	@NotNull
	private Integer subscription;

	/**
	 * The requested CPU
	 */
	@NotNull
	@Min(0)
	private Double cpu;

	/**
	 * The requested memory in MB.
	 */
	@NotNull
	@Min(0)
	private Integer ram;

	/**
	 * The requested OS. May be <code>null</code> and defaulted to the #instancePrice 's OS.
	 */
	private VmOs os;

	/**
	 * The optional requested CPU behavior. When <code>false</code>, the CPU is
	 * variable, with boost mode.
	 */
	private Boolean constant;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance,
	 * does not consider the {@link #quantityMax} or {@link #quantityMin}. When
	 * <code>null</code>, there is no limit. Only relevant for variable instance
	 * price type such as AWS Spot.
	 */
	@Min(0)
	private Double maxVariableCost;

	/**
	 * The Internet access : Internet facing, etc.
	 */
	@NotNull
	private InternetAccess internet = InternetAccess.PRIVATE;

	/**
	 * The minimal quantity of this instance.
	 */
	@Min(0)
	@NotNull
	private Integer minQuantity = 1;

	/**
	 * The maximal quantity of this instance. When defined, must be greater than
	 * {@link #quantityMin}
	 */
	@Min(1)
	private Integer maxQuantity;
}
