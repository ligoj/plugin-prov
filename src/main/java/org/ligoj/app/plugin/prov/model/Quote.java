package org.ligoj.app.plugin.prov.model;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractNamedAuditedEntity;

import lombok.Getter;
import lombok.Setter;

/**
 * A saved quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_VM_QUOTE", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class Quote extends AbstractNamedAuditedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Monthly cost, computed during the creation.
	 */
	@NotNull
	@Min(0)
	private Double cost;

	/**
	 * The related node (VM provider) of this quote.
	 */
	@NotNull
	@ManyToOne
	private Node node;

	/**
	 * Quoted instance.
	 */
	@OneToMany(mappedBy = "quote")
	private Set<QuoteInstance> instances;

}
