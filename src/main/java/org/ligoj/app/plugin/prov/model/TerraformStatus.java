package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.ligoj.app.model.AbstractLongTaskNode;

import lombok.Getter;
import lombok.Setter;

/**
 * Terraform status.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TERRAFORM_STATUS", uniqueConstraints = @UniqueConstraint(columnNames = "locked"))
public class TerraformStatus extends AbstractLongTaskNode {

	/**
	 * The subscription identifier requesting this task.
	 */
	private int subscription;

	/**
	 * The command index within the sequence. May be <code>null</code>
	 */
	private Integer commandIndex;

	/**
	 * The commands sequence using <code>,</code> char as separator. Example: <code>init,plan,show,apply,destroy</code>
	 */
	private String sequence;

	/**
	 * Resources to be added.
	 */
	private int added;

	/**
	 * Resources to be deleted.
	 */
	private int deleted;

	/**
	 * Resources to be updated.
	 */
	private int updated;

	/**
	 * Resources being processed. Only computed while requesting the task.
	 */
	@Transient
	private int processing;

	/**
	 * Completed resources. Only computed while requesting the task.
	 */
	@Transient
	private int completed;

}
