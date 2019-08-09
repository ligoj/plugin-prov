/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import javax.validation.constraints.Size;

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

	/**
	 * Optional name.
	 */
	@Size(min = 1, max = 255)
	private String name;

}
