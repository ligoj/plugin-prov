/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import org.ligoj.app.plugin.prov.model.ResourceType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * The updated cost of an updated resource..
 */
@Getter
@Setter
@RequiredArgsConstructor
public class UpdatedCost {

	/**
	 * The quote resource identifier. For mass operation this object is <code>null</code>.
	 */
	private final Integer id;

	/**
	 * The new total quote cost.
	 */
	private FloatingCost total;

	/**
	 * The new resource cost. <code>null</code> for deleted resource.
	 */
	private FloatingCost cost;

	/**
	 * The new related costs of the update resource. For sample, storage for associated instance. The primary key is the
	 * resource type: <code>instance</code>, <code>storage</code>, <code>support</code>. The secondary key is the
	 * resource identifier.
	 */
	private Map<ResourceType, Map<Integer, FloatingCost>> related = new EnumMap<>(ResourceType.class);

	/**
	 * The new related deleted costs of the update resource. For sample, storage for associated instance. The primary
	 * key is the resource type: <code>instance</code>, <code>storage</code>, <code>support</code>. The secondary key is
	 * the resource identifier.
	 */
	private Map<ResourceType, Collection<Integer>> deleted = new EnumMap<>(ResourceType.class);
}
