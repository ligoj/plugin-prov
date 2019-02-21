/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;

import org.ligoj.app.plugin.prov.model.QuoteVm;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quote instance query.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractQuoteInstanceQuery implements QuoteVm {

	@DefaultValue(value = "1")
	@QueryParam("cpu")
	@Builder.Default
	private double cpu = 1;

	@DefaultValue(value = "1")
	@QueryParam("ram")
	@Builder.Default
	private int ram = 1;

	@QueryParam("constant")
	private Boolean constant;

	@QueryParam("type")
	private String type;

	@QueryParam("location")
	private String location;

	@QueryParam("usage")
	private String usage;

	@QueryParam("license")
	private String license;

	@Override
	public String getLocationName() {
		return getLocation();
	}

	@Override
	public String getUsageName() {
		return getUsage();
	}
}
