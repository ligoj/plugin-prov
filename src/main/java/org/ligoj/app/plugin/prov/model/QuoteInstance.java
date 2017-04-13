package org.ligoj.app.plugin.prov.model;

import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.Quote;
import org.ligoj.app.plugin.prov.model.QuoteStorage;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A configured instance inside a quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_VM_QUOTE_INSTANCE")
public class QuoteInstance extends AbstractPersistable<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Related instance with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvInstancePrice instance;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne
	@JsonIgnore
	private Quote quote;

	/**
	 * Attached storages.
	 */
	@OneToMany(mappedBy = "instance")
	private Set<QuoteStorage> storages;

}
