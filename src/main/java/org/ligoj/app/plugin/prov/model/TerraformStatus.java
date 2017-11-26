package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.ligoj.app.model.AbstractLongTaskNode;
import org.ligoj.app.plugin.prov.terraform.TerraformStep;

import lombok.Getter;
import lombok.Setter;

/**
 * Terraform status.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TERRAFORM_STATUS", uniqueConstraints=@UniqueConstraint(columnNames="locked"))
public class TerraformStatus extends AbstractLongTaskNode {

	/**
	 * The progress of the execution.
	 */
	private TerraformStep step;

}
