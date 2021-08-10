/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;

import org.ligoj.app.plugin.prov.model.ResourceType;

import lombok.Getter;
import lombok.Setter;

/**
 *
 * Network object for edition.
 */
@Getter
@Setter
public class NetworkVo extends AbstractNetworkVo {

	/**
	 * The related peer resource identifier.
	 */
	@NotNull
	private Integer peer;

	/**
	 * The peer resource type.
	 */
	@NotNull
	private ResourceType peerType;

}
