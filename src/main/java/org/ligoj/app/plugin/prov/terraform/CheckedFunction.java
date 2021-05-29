/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;

/**
 * A consumer throwing {@link IOException}.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 * @param <E> The checked exception type.
 */
@FunctionalInterface
public interface CheckedFunction<T, R, E extends Throwable> {

	/**
	 * Applies this function to the given argument.
	 *
	 * @param t the function argument
	 * @return the function result
	 * @throws E When the operations failed.
	 */
	R apply(T t) throws E;
}