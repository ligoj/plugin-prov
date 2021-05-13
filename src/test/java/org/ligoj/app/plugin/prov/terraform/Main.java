/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

/**
 * Fake command to handle test of process builder invocation.
 */
class Main {

	/**
	 * Only the Java process bootstrap.
	 *
	 * @param strings The options. The first parameter, when defined and with the form like <code>error=$code</code>
	 *                will be used to generate an exit code <code>System.exit($code)</code>.
	 */
	public static void main(final String... strings) {
		// Basic log to check the I/O
		System.out.println("Message standard : " + String.join(",", strings));
		System.err.println("Message error : " + String.join(",", strings));

		// Exit code handling
		if (strings.length >= 1 && strings[0].startsWith("error=")) {
			// Custom error code
			System.exit(Integer.parseInt(strings[0].split("=")[1]));
		}

		// No error code
		System.exit(2);
	}
}
