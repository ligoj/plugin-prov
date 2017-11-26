package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractNamedEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Location for a VM provider. The name attribute is an unique location name within the VM provider.
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_LOCATION", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvLocation extends AbstractNamedEntity<Integer> {

	/**
	 * The related node (VM provider) of this location.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;

}
