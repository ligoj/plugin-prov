package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A storage configuration linked to an instance inside a quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_STORAGE")
public class QuoteStorage extends AbstractPersistable<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 4795855466011388616L;

	/**
	 * Size of the storage in "Go" "Giga Bytes"
	 */
	@NotNull
	private Integer quantity;

	/**
	 * Related storage with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvStorage storage;

	/**
	 * Linked quoted instance.
	 */
	@NotNull
	@ManyToOne
	@JsonIgnore
	private QuoteInstance instance;

}
