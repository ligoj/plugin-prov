package org.ligoj.app.plugin.prov.terraform;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;
import org.mockito.plugins.MockMaker;
import org.objenesis.Objenesis;

import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;

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
		final Class<?>[] erasedClasses = new Class[] { TerraformUtils.class, SystemUtils.class, Mockito.class, ConfigurationResource.class,
				MockMaker.class, ClassLoadingStrategy.class, Objenesis.class };
		final URL[] erasedCL = new URL[erasedClasses.length];
		for (int index = 0; index < erasedClasses.length; index++) {
			final Class<?> erasedClass = erasedClasses[index];
			erasedCL[index] = erasedClass.getProtectionDomain().getCodeSource().getLocation();
		}
		ThreadClassLoaderScope scope = null;
		try {
			System.setProperty("os.name", os);
			final URLClassLoader urlClassLoader = new URLClassLoader(erasedCL, null);// ,
																						// urlClassLoader0);
			scope = new ThreadClassLoaderScope(urlClassLoader);
			final Object terra = urlClassLoader.loadClass("org.ligoj.app.plugin.prov.terraform.TerraformUtils").getDeclaredConstructor()
					.newInstance();
			final Object mock = MethodUtils.invokeStaticMethod(urlClassLoader.loadClass("org.mockito.Mockito"), "mock",
					urlClassLoader.loadClass("org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource"));
			FieldUtils.writeField(terra, "configuration", mock, true);
			Assertions.assertEquals(Arrays.asList(command),
					((ProcessBuilder) MethodUtils.invokeMethod(terra, true, "newBuilder", new Object[] { new String[] { "terraform" } }))
							.command());
		} finally {
			IOUtils.closeQuietly(scope);
		}
	}

	@AfterEach
	public void restoreOs() {
		System.setProperty("os.name", os);
	}
}
