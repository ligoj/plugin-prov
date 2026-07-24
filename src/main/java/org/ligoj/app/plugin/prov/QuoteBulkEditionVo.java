/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;
import lombok.Setter;

/**
 * A bulk patch applied to several compute resources of a quote in one server-side transaction. Each patched field uses
 * a tri-state convention: <code>null</code> (absent) leaves the resources untouched, an empty string clears the value
 * (back to the quote-level inheritance), any other value is resolved by name and applied. Prices are re-resolved per
 * resource after the patch.
 */
@Getter
@Setter
public class QuoteBulkEditionVo {

	/**
	 * The identifiers of the resources to patch. All must belong to the subscription's quote.
	 */
	@NotEmpty
	private List<Integer> ids;

	/**
	 * Optional usage profile name. Empty string clears the resource-level override.
	 */
	private String usage;

	/**
	 * Optional budget profile name. Empty string clears the resource-level override.
	 */
	private String budget;

	/**
	 * Optional optimizer profile name. Empty string clears the resource-level override.
	 */
	private String optimizer;

	/**
	 * Optional location name. Empty string clears the resource-level override (inherit the quote location).
	 */
	private String location;

	/**
	 * Optional license model. Empty string clears the resource-level override.
	 */
	private String license;
}
