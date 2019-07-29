/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.Range;
import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.INamableBean;
import org.ligoj.bootstrap.core.model.AbstractPersistable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A network link between a source and a target resource. The set
 * <code>source, target, sourceType, targetType, port</code> is unique.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_NETWORK", uniqueConstraints = @UniqueConstraint(columnNames = { "source", "source_type",
		"target", "target_type", "port", "configuration" }))
@JsonIgnoreProperties(value = "id")
public class ProvNetwork extends AbstractPersistable<Integer>
		implements Configurable<ProvQuote, Integer>, INamableBean<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Optional name.
	 */
	@Size(min = 1, max = 255)
	private String name;

	/**
	 * The related source resource identifier. This is not a strong relationship, and this is intended. We don't want to
	 * handle one column per resource type.
	 */
	@NotNull
	private Integer source;

	/**
	 * The source resource type.
	 */
	@NotNull
	private ResourceType sourceType;

	/**
	 * The related target resource identifier. This is not a strong relationship, and this is intended. We don't want to
	 * handle one column per resource type.
	 */
	@NotNull
	private Integer target;

	/**
	 * The target resource type.
	 */
	@NotNull
	private ResourceType targetType;

	/**
	 * The destination port number.
	 */
	@NotNull
	@Range(min = 1, max = 65535)
	private Integer port;

	/**
	 * Optional frequency. The period is not yet specified. Might be second, or month... Whatever, it should be fixed
	 * for all work among the related subscription.
	 */
	private Integer rate;

	/**
	 * Optional throughput in KiB/s.
	 */
	private Integer throughput;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

}
