package org.ligoj.app.plugin.prov.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A configured instance inside a quote. Name is unique inside a quote. The
 * instance cost does not include the associated storages.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_INSTANCE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "configuration" }))
public class ProvQuoteInstance extends AbstractQuoteResource {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Related instance with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvInstancePrice price;

	/**
	 * The requested CPU.
	 */
	@NotNull
	@PositiveOrZero
	private Double cpu;

	/**
	 * The optional maximum monthly cost you want to pay. Only for one instance,
	 * does not consider the {@link #minQuantity} or {@link #maxQuantity}. When
	 * <code>null</code>, there is no limit. Only relevant for variable instance
	 * price type such as AWS Spot.
	 */
	@Positive
	private Double maxVariableCost;

	/**
	 * The requested RAM in "MiB". 1MiB = 1024 MiB.
	 */
	@NotNull
	@PositiveOrZero
	private Integer ram;

	/**
	 * The requested OS. May be different from the one related by
	 * {@link #price}, but refers to {@link VmOs#toPricingOs()}
	 */
	@NotNull
	private VmOs os;

	/**
	 * The requested CPU behavior. When <code>false</code>, the CPU is variable,
	 * with boost mode.
	 */
	private Boolean constant;

	/**
	 * The Internet access : Internet facing, etc.
	 */
	@NotNull
	private InternetAccess internet = InternetAccess.PUBLIC;

	@JsonIgnore
	@OneToMany(mappedBy = "quoteInstance", cascade = CascadeType.REMOVE)
	private List<ProvQuoteStorage> storages;

	/**
	 * The minimal quantity of this instance.
	 */
	@NotNull
	@PositiveOrZero
	private Integer minQuantity = 1;

	/**
	 * The maximal quantity of this instance. May be <code>null</code> when unbound
	 * maximal, otherwise must be greater than {@link #minQuantity}
	 */
	@PositiveOrZero
	private Integer maxQuantity = 1;

	/**
	 * Optional usage for this resource when different from the related quote.
	 */
	@ManyToOne
	private ProvUsage usage;

	/**
	 * The instance could be terminated by the provider.
	 */
	private boolean ephemeral;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return maxQuantity == null;
	}

}
