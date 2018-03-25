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

	private String version;
	private boolean installed;
	private String lastVersion;

}
