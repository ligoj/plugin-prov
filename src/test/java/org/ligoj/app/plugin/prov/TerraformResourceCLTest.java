package org.ligoj.app.plugin.prov;

import java.net.URL;
import java.net.URLClassLoader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test class of {@link TerraformResource} for {@link ProcessBuilder} platform only.
 */
public class TerraformResourceCLTest {

	private String os = SystemUtils.OS_NAME;

	@Test
	public void newBuildWindows() throws ReflectiveOperationException {
		checkCommands("Windows", new String[] { "cmd.exe", "/c", "terraform" });
	}

	@Test
	public void newBuildOther() throws ReflectiveOperationException {
		checkCommands("FreeBSD", new String[] { "sh", "-c", "terraform" });
	}

	@SuppressWarnings("unchecked")
	private void checkCommands(final String os, final String... command)
			throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final URL[] urLs = ((URLClassLoader) Main.class.getClassLoader()).getURLs();
		ThreadClassLoaderScope scope = null;
		try {
			System.setProperty("os.name", os);
			final URLClassLoader urlClassLoader = new URLClassLoader(urLs, null);
			scope = new ThreadClassLoaderScope(urlClassLoader);
			Assert.assertEquals(command,
					((Class<TerraformResource>) urlClassLoader.loadClass(TerraformResource.class.getName()))
							.newInstance().newBuilder("some").command());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	@After
	public void restoreOs() {
		System.setProperty("os.name", os);
	}
}
