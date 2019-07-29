/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.Range;
import org.ligoj.app.plugin.prov.model.ResourceType;

import lombok.Getter;
import lombok.Setter;

/**
 * 
 * Network object for edition.
 */
@Getter
@Setter
public class NetworkVo {

	/**
	 * When <code>true</code> is inbound network. Otherwise, is outbound network. By default, is out.
	 */
	private boolean inbound;

	/**
	 * Optional name.
	 */
	@Size(min = 1, max = 255)
	private String name;

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

	/**
	 * The peer port number.
	 */
	@NotNull
	@Range(min = 1, max = 65535)
	private Integer port;

	/**
	 * Optional frequency. The period is not yet specified. Might be second, or month... Whatever, it should be fixed
	 * for all work among the related subscription.
	 */
	@PositiveOrZero
	private Integer rate;

	/**
	 * Optional throughput in KiB/s.
	 */
	@PositiveOrZero
	private Integer throughput;

}
