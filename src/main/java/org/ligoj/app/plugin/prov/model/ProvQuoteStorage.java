package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.bootstrap.core.model.ToIdSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;

/**
 * A storage configuration inside a quote and optionally linked to an instance. Name is unique inside a quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_STORAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteStorage extends AbstractQuoteResource {

	/**
	 * Optional required latency class.
	 */
	@Enumerated(EnumType.ORDINAL)
	private Rate latency;

	/**
	 * Optional required optimized best usage of this storage
	 */
	private ProvStorageOptimized optimized;

	/**
	 * Optional instance compatibility flag.
	 */
	private Boolean instanceCompatible;

	/**
	 * Required size of the storage in "GiB". 1GiB = 1024MiB
	 */
	@NotNull
	private Integer size;

	/**
	 * Related storage with the price.
	 */
	@NotNull
	@ManyToOne
	private ProvStoragePrice price;

	/**
	 * Optional linked quoted instance.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteInstance quoteInstance;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return getQuoteInstance() != null && getQuoteInstance().isUnboundCost();
	}

}
