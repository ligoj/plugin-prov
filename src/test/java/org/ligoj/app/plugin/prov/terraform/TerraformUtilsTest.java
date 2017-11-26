package org.ligoj.app.plugin.prov.terraform;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test class of {@link TerraformUtils}
 */
public class TerraformUtilsTest {

	private String os = SystemUtils.OS_NAME;

	@Test
	public void newBuildWindows() throws ReflectiveOperationException {
		checkCommands("Windows", new String[] { "cmd.exe", "/c", "null terraform" });
	}

	@Test
	public void newBuildOther() throws ReflectiveOperationException {
		checkCommands("FreeBSD", new String[] { "sh", "-c", "null terraform" });
	}

	private void checkCommands(final String os, final String... command) throws ReflectiveOperationException {
		final URL[] urLs = ((URLClassLoader) Main.class.getClassLoader()).getURLs();
		ThreadClassLoaderScope scope = null;
		try {
			System.setProperty("os.name", os);
			final URLClassLoader urlClassLoader = new URLClassLoader(urLs, null);
			scope = new ThreadClassLoaderScope(urlClassLoader);
			final Object terra = urlClassLoader.loadClass("org.ligoj.app.plugin.prov.terraform.TerraformUtils").newInstance();
			final Object mock = MethodUtils.invokeStaticMethod(urlClassLoader.loadClass("org.mockito.Mockito"), "mock",
					urlClassLoader.loadClass("org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource"));
			FieldUtils.writeField(terra, "configuration", mock, true);
			Assert.assertEquals(Arrays.asList(command),
					((ProcessBuilder) MethodUtils.invokeMethod(terra, true, "newBuilder", new Object[] { new String[] { "terraform" } }))
							.command());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	@After
	public void restoreOs() {
		System.setProperty("os.name", os);
	}
}
