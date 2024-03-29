/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.ligoj.app.plugin.prov.dao.Optimizer;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Optimizer profile
 */
@Getter
@Setter
@Accessors(chain = true)
@Entity
@Table(name = "LIGOJ_PROV_OPTIMIZER", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "configuration" }))
public class ProvOptimizer extends AbstractMultiScoped {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The maximal accepted initial cost.
	 */
	@Enumerated(EnumType.STRING)
	private Optimizer mode = Optimizer.COST;

}
