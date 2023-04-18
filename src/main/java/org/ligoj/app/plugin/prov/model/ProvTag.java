/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;

import org.ligoj.app.model.Configurable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * A tag on a resource. A tag is composed by a name and an optional value. The couple <code>name,value</code> is unique
 * for a resource. So a resource can have several tags having the same name but a different value.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TAG", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "value", "type",
		"resource", "configuration" }))
public class ProvTag extends AbstractProvTag implements Configurable<ProvQuote, Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The parent quote.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private ProvQuote configuration;

}
