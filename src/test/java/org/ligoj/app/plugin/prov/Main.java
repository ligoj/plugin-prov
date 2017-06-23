package org.ligoj.app.plugin.prov;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Fake command to handle test of process builder invocation.
 */
public class Main {

	public static void main(final String... strings) {
		// Basic log to check the I/O
		System.out.println("Message standard : " + Arrays.stream(strings).collect(Collectors.joining(",")));
		System.err.println("Message error : " + Arrays.stream(strings).collect(Collectors.joining(",")));

		// Exit code handling
		if (strings.length >= 1 && strings[0].startsWith("error=")) {
			// Custom error code
			System.exit(Integer.valueOf(strings[0].split("=")[1]));
		}

		// No error code
		System.exit(0);
	}
}
