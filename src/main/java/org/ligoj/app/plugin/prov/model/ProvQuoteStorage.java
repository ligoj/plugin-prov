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
import javax.validation.constraints.Positive;

import org.apache.commons.lang3.ObjectUtils;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.model.ToIdSerializer;
import org.springframework.data.domain.Persistable;

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
public class ProvQuoteStorage extends AbstractQuote<ProvStoragePrice> implements QuoteStorage {

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
	@Positive
	private int size;

	/**
	 * The maximal used size. When <code>null</code>, the requested size is used.
	 *
	 * @see #sizeMax
	 */
	@Positive
	private Integer sizeMax;

	/**
	 * Optional linked quoted instance.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteInstance quoteInstance;

	/**
	 * Optional linked quoted container.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteContainer quoteContainer;

	/**
	 * Optional linked quoted database.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteDatabase quoteDatabase;

	/**
	 * Optional linked quoted function.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonSerialize(using = ToIdSerializer.class)
	private ProvQuoteFunction quoteFunction;

	/**
	 * The quantity of this instance. When <code>null</code>, is considered as <code>1</code>.
	 */
	private Integer quantity = 1;

	/**
	 * Resolved price configuration.
	 */
	@NotNull
	@ManyToOne
	private ProvStoragePrice price;

	@Override
	public ResourceType getResourceType() {
		return ResourceType.STORAGE;
	}

	@Override
	@JsonIgnore
	public boolean isUnboundCost() {
		return Optional.ofNullable(getQuoteResource()).map(AbstractQuoteVm::isUnboundCost).orElse(false);
	}

	/**
	 * Return the optional associated resource: instance or database.
	 *
	 * @return the optional associated resource: instance or database.
	 */
	@JsonIgnore
	public AbstractQuoteVm<?> getQuoteResource() {
		return ObjectUtils.firstNonNull(quoteDatabase, quoteInstance, quoteContainer, quoteFunction);
	}

	@Override
	@JsonIgnore
	public Integer getInstance() {
		return Optional.ofNullable(getQuoteInstance()).map(Persistable::getId).orElse(null);
	}

	@Override
	@JsonIgnore
	public Integer getDatabase() {
		return Optional.ofNullable(getQuoteDatabase()).map(Persistable::getId).orElse(null);
	}

	@Override
	@JsonIgnore
	public Integer getContainer() {
		return Optional.ofNullable(getQuoteContainer()).map(Persistable::getId).orElse(null);
	}

	@Override
	@JsonIgnore
	public Integer getFunction() {
		return Optional.ofNullable(getQuoteFunction()).map(Persistable::getId).orElse(null);
	}

	@Override
	@JsonIgnore
	public String getLocationName() {
		return Optional.ofNullable(getLocation()).map(INamableBean::getName).orElse(null);
	}
}
