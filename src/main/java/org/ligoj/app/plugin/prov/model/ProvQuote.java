package org.ligoj.app.plugin.prov.model;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.PluginConfiguration;
import org.ligoj.app.model.Subscription;
import org.ligoj.bootstrap.core.model.AbstractDescribedAuditedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A saved quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE")
public class ProvQuote extends AbstractDescribedAuditedEntity<Integer> implements PluginConfiguration {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Minimal monthly cost, computed during the creation and kept synchronized
	 * with the updates.
	 */
	@NotNull
	@Min(0)
	private Double cost = 0d;

	/**
	 * Maximal determined monthly cost, computed during the creation and kept
	 * synchronized with the updates. When there are unbound maximal quantities
	 * (<code>null</code>), the minimal cost is used and the
	 * {@link #unboundCostCounter} is incremented.
	 */
	@NotNull
	@Min(0)
	private Double maxCost = 0d;

	/**
	 * The amount unbound maximal quantities. Would be used to track the unbound
	 * monthly bills on different scenarios.
	 */
	@NotNull
	@Min(0)
	private Integer unboundCostCounter = 0;

	/**
	 * The related subscription.
	 */
	@NotNull
	@ManyToOne
	private Subscription subscription;

	/**
	 * Quoted instance.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteInstance> instances;

	/**
	 * Quoted storages.
	 */
	@OneToMany(mappedBy = "configuration", cascade = CascadeType.REMOVE)
	@JsonIgnore
	private List<ProvQuoteStorage> storages;

}
