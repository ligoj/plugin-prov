package org.ligoj.app.plugin.prov.model;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import org.ligoj.app.model.AbstractLongTaskNode;
import org.ligoj.app.plugin.prov.terraform.TerraformSequence;

import lombok.Getter;
import lombok.Setter;

/**
 * Terraform status.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_TERRAFORM_STATUS", uniqueConstraints = @UniqueConstraint(columnNames = "locked"))
public class TerraformStatus extends AbstractLongTaskNode implements Serializable {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The sequence type.
	 */
	@Enumerated(EnumType.STRING)
	private TerraformSequence type;

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
	 * Resources to be replaced. Count as 2 changes.
	 */
	private int toReplace;

	/**
	 * Resources to be deleted.
	 */
	private int toDestroy;

	/**
	 * Resources to be updated.
	 */
	private int toUpdate;

	/**
	 * Changes being processed. Only computed while requesting the task.
	 */
	@Transient
	private int completing;

	/**
	 * Changes resources. Only computed while requesting the task.
	 */
	@Transient
	private int completed;

}
