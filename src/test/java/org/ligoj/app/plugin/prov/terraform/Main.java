/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

/**
 * Fake command to handle test of process builder invocation.
 */
public class Main {

	public static void main(final String... strings) {
		// Basic log to check the I/O
		System.out.println("Message standard : " + String.join(",", strings));
		System.err.println("Message error : " + String.join(",", strings));

		// Exit code handling
		if (strings.length >= 1 && strings[0].startsWith("error=")) {
			// Custom error code
			System.exit(Integer.valueOf(strings[0].split("=")[1]));
		}

		// No error code
		System.exit(2);
	}
}
