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
	 * The command index within the sequence currently executed. May be <code>null</code> when not yet started.
	 */
	private Integer commandIndex;

	/**
	 * The commands sequence using <code>,</code> char as separator. Example: <code>init,plan,show,apply,destroy</code>
	 */
	private String sequence;

	/**
	 * Resources to be added.
	 */
	private int toAdd;

	/**
	 * Resources to be deleted.
	 */
	private int toDestroy;

	/**
	 * Resources to be updated.
	 */
	private int toChange;

	/**
	 * Resources being processed. Only computed while requesting the task.
	 */
	@Transient
	private int completing;

	/**
	 * Completed resources. Only computed while requesting the task.
	 */
	@Transient
	private int completed;

}
