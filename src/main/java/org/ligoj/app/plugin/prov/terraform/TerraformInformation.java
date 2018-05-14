/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

/**
 * Terraform information.
 */
@Getter
@Setter
public class TerraformInformation implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	private String version;
	private boolean installed;
	private String lastVersion;

}
