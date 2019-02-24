/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.quote.storage;

import javax.validation.constraints.Positive;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.QuoteStorage;
import org.ligoj.app.plugin.prov.model.Rate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quote instance query.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteStorageQuery implements QuoteStorage {

	/**
	 * The requested size in GiB.
	 */
	@Builder.Default
	@DefaultValue(value = "1")
	@QueryParam("size")
	@Positive
	private int size = 1;

	/**
	 * The optional requested minimal {@link Rate} class.
	 */
	@QueryParam("latency")
	private Rate latency;

	/**
	 * The optional requested quote instance to be associated. Cannot be not <code>null</code> with {@link #database}.
	 */
	@QueryParam("instance")
	private Integer instance;

	/**
	 * The optional requested quote database to be associated. Cannot be not <code>null</code> with {@link #instance}.
	 */
	@QueryParam("database")
	private Integer database;

	/**
	 * The optional requested {@link ProvStorageOptimized}.
	 */
	@QueryParam("optimized")
	private ProvStorageOptimized optimized;

	/**
	 * Optional location name. May be <code>null</code>.
	 */
	@QueryParam("location")
	private String location;

	@Override
	@JsonIgnore
	public String getLocationName() {
		return location;
	}
}
