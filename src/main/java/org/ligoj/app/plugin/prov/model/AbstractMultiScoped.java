/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A resource associated to multiple level: VM and quote.
 */
@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AbstractMultiScoped extends AbstractNamedEntity<Integer>
		implements Configurable<ProvQuote, Integer> {

	/**
	 * Default SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The related quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

}
