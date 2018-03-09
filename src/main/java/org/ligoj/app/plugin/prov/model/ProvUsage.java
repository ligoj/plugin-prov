package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.Max;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Named resource usage : 1 to 100. Corresponds to percentage.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_USAGE", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "configuration" }))
public class ProvUsage extends AbstractNamedEntity<Integer> implements Configurable<ProvQuote, Integer> {

	/**
	 * The related quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

	/**
	 * Usage rate base 100.
	 */
	@Positive
	@Max(100)
	@NotNull
	private Integer rate = 100;

	/**
	 * Duration of this usage in month. TODO Make this attribute as "int"
	 */
	@Positive
	private Integer duration = 1;

}
