/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.Range;

import lombok.Getter;
import lombok.Setter;

/**
 * Network base class.
 */
@Getter
@Setter
public abstract class AbstractNetworkVo {

	/**
	 * When <code>true</code> is inbound network. Otherwise, is outbound network. By default, is out.
	 */
	private boolean inbound;

	/**
	 * The peer port number.
	 */
	@Range(min = 1, max = 65535)
	private Integer port;

	/**
	 * Optional workload frequency, in seconds: <code>3600</code> for an hourly workload, <code>86400</code> for a
	 * daily one, ... <code>null</code> or <code>0</code> for a continuous workload (the default).
	 */
	@PositiveOrZero
	private Integer rate;

	/**
	 * Optional throughput in KiB/s.
	 */
	@PositiveOrZero
	private Integer throughput;

	/**
	 * Optional name.
	 */
	@Size(min = 1, max = 255)
	private String name;

}
