/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ResourceType;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * Network object for edition with source and destination peers.
 */
@Getter
@Setter
public class NetworkFullVo extends NetworkVo {

	/**
	 * The related peer resource identifier.
	 */
	@NotNull
	private Integer source;

	/**
	 * The peer resource type.
	 */
	@NotNull
	private ResourceType sourceType;

}
