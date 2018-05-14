/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;

/**
 * A consumer throwing {@link IOException}.
 * 
 * @param <T>
 *            The consumed resource type.
 * @param <E>
 *            The checked exception type.
 */
@FunctionalInterface
public interface CheckedConsumer<T, E extends Throwable> {

	/**
	 * Handle the given resource.
	 * 
	 * @param t
	 *            The resource.
	 * @throws E
	 *             When the operations failed.
	 */
	void accept(T t) throws E;
}