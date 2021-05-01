/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * Network object for edition with source and destination peers.
 */
@Getter
@Setter
public class NetworkFullByNameVo extends AbstractNetworkVo {

	/**
	 * The related peer resource identifier. Names must be unique among all resource names.
	 */
	@NotNull
	private String source;

	/**
	 * The related peer resource identifier. Names must be unique among all resource names.
	 */
	@NotNull
	private String peer;

}
