/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import java.util.Optional;

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
 * A storage configuration inside a quote and optionally linked to an instance or database. Name is unique inside a
 * quote.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_QUOTE_STORAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name",
		"configuration" }))
public class ProvQuoteStorage extends AbstractQuoteResource<ProvStoragePrice> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

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
	 * Required size of the storage in "GiB". 1GiB = 1024MiB
	 */
	@NotNull
	private Integer size;

	/**
	 * Optional linked quoted instance.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteInstance quoteInstance;

	/**
	 * Optional linked quoted database.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteDatabase quoteDatabase;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvStoragePrice price;

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return Optional.ofNullable(getQuoteResource()).map(AbstractQuoteResourceInstance::isUnboundCost).orElse(false);
	}

	/**
	 * Return the optional associated resource: instance or database.
	 *
	 * @return the optional associated resource: instance or database.
	 */
	public AbstractQuoteResourceInstance<?> getQuoteResource() {
		return quoteInstance == null ? quoteDatabase : quoteInstance;
	}
}
