package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;

import org.ligoj.app.model.AbstractLongTask;
import org.ligoj.app.plugin.prov.TerraformStep;

import lombok.Getter;
import lombok.Setter;

/**
 * Terraform status.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TERRAFORM_STATUS")
public class TerraformStatus extends AbstractLongTask {

	private static final long serialVersionUID = 1L;

	/**
	 * The progress of the execution.
	 */
	private TerraformStep step;

}
