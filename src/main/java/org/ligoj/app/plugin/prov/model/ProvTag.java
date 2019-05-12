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

import org.ligoj.app.model.Configurable;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A tag on a resource. A tag is composed by a name and an optional value. The couple <code>name,value</code> is unique
 * for a resource. So a resource can have several tags having the same name by a different value.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TAG", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "value", "type",
		"resource", "configuration" }))
public class ProvTag extends AbstractNamedEntity<Integer> implements Configurable<ProvQuote, Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The attached resource type.
	 */
	@NotNull
	private ResourceType type;

	/**
	 * The optional tag value.
	 */
	@Size(max = 1024, min = 1)
	private String value;

	/**
	 * The related resource identifier. This is not a strong relationship, and this is intended. We don't want to handle
	 * one column per resource type.
	 */
	@NotNull
	private Integer resource;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

}
