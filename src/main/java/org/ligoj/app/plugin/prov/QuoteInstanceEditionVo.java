package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import org.ligoj.app.plugin.prov.model.InternetAccess;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Quote for an instance while editing it.
 */
@Getter
@Setter
public class QuoteInstanceEditionVo extends DescribedBean<Integer> {

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
	private Double cpu;

	/**
	 * The requested memory in MB.
	 */
	@NotNull
	@Positive
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
	@Positive
	private Double maxVariableCost;

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
	 * The maximal quantity of this instance. When defined, must be greater than
	 * {@link #quantityMin}
	 */
	@PositiveOrZero
	private Integer maxQuantity;

	/**
	 * The instance could be terminated by the provider. Default <code>false</code>.
	 */
	private boolean ephemeral;
	
	/**
	 * Optional required location. When <code>null</code>, the default quote's one will be used.
	 */
	private String location;
}
