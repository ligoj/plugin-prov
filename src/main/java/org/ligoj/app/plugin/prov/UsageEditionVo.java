package org.ligoj.app.plugin.prov;

import javax.validation.constraints.Max;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

/**
 * Usage definition inside a quote.
 */
@Getter
@Setter
public class UsageEditionVo {

	/**
	 * Unique name within a quote.
	 */
	@NotBlank
	@NotNull
	private String name;

	/**
	 * Usage rate base 100.
	 */
	@Positive
	@Max(100)
	@NotNull
	private Integer rate = 100;

	/**
	 * Usage duration in months.
	 */
	@Positive
	@Max(72)
	private int duration = 1;

}
