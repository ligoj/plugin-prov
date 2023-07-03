/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.zip.ZipOutputStream;

import jakarta.transaction.Transactional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.core5.http.HttpStatus;
import org.eclipse.jetty.util.thread.ThreadClassLoaderScope;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.resource.plugin.LigojPluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link TerraformUtils}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class TerraformUtilsTest extends AbstractServerTest {

	private static final File MOCK_PATH = new File("target/test-classes/terraform-test").getAbsoluteFile();

	private static final File EMPTY_PATH = new File("target/test-classes/terraform-empty").getAbsoluteFile();

	@Autowired
	private TerraformUtils resource;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	void reset() {
		cacheManager.getCache("configuration").clear();
		cacheManager.getCache("terraform-version-latest").clear();
		cacheManager.getCache("terraform-version").clear();
	}

	@Test
	void getCurrentOs() {
		Assertions.assertNotNull(resource.getCurrentOs());
	}

	@Test
	void getOsValue() {
		final var resource = new TerraformUtils();
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "UNKNOWN"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD 1.0"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD 1.0 A"));
		Assertions.assertEquals("terraform.exe", resource.getOsValue(resource.bins, "Windows 2012"));
	}

	// Coverage only
	@Test
	void getClassLoader() {
		Assertions.assertNull(resource.getClassLoader());
		Assertions.assertNotNull(resource.getCurrentOs());
	}

	@Test
	void newBuilderOther() {
		checkNewBuilderBsd("UNKNOWN");
	}

	@Test
	void newBuilderBsd() {
		checkNewBuilderBsd("FreeBSD");
	}

	@Test
	void newBuilderBsd2() {
		checkNewBuilderBsd("FreeBSD 1.0");
	}

	@Test
	void newBuilderBsd3() {
		checkNewBuilderBsd("FreeBSD 1.0 A");
	}

	void checkNewBuilderBsd(final String os) {
		checkNewBuilder(os, new String[] { "sh", "-c" }, "terraform arg1 arg2");
	}

	@Test
	void newBuilderWindows() {
		checkNewBuilder("Windows Server 2012", new String[] { "cmd.exe", "/c" }, "terraform.exe arg1 arg2");
	}

	@Test
	void newBuilderNotInstalled() {
		final var classLoader = Mockito.mock(LigojPluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(EMPTY_PATH.toPath());
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected LigojPluginsClassLoader getClassLoader() {
				return classLoader;
			}

		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(utils);
		Assertions.assertEquals("terraform-not-found",
				Assertions.assertThrows(BusinessException.class, () -> utils.newBuilder("arg1", "arg2")).getMessage());
	}

	private void checkNewBuilder(final String os, final String[] args, final String command) {
		final var classLoader = Mockito.mock(LigojPluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(MOCK_PATH.toPath());
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected LigojPluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			protected String getCurrentOs() {
				return os;
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(utils);
		final var newBuilder = utils.newBuilder("arg1", "arg2");
		Assertions.assertEquals(args[0], newBuilder.command().get(0));
		Assertions.assertEquals(args[1], newBuilder.command().get(1));
		Assertions.assertTrue(newBuilder.command().get(2).endsWith(command));
	}

	@Test
	void install() throws IOException {
		final var utils = prepareForInstall("mock-server/prov/terraform/terraform.zip");
		utils.install();

		// Check file exists and runnable
		final var pathDownload = new File("target/test-classes/terraform-download").getAbsoluteFile();
		final File file;
		if (SystemUtils.IS_OS_WINDOWS) {
			file = new File(pathDownload, "prov/terraform.exe");
		} else {
			file = new File(pathDownload, "prov/terraform");
		}
		Assertions.assertTrue(file.exists());
		try (var input = new FileInputStream(file)) {
			Assertions.assertTrue(IOUtils.toString(input, StandardCharsets.UTF_8).startsWith("#EMPTY"));
		}
	}

	@Test
	void installInvalidZip() throws IOException {
		final var utils = prepareForInstall("mock-server/prov/terraform/terraform-invalid.zip");
		Assertions.assertThrows(FileNotFoundException.class, utils::install);
	}

	private TerraformUtils prepareForInstall(final String file) throws IOException {

		// Prepare the download
		final var pathDownload = new File("target/test-classes/terraform-download").getAbsoluteFile();
		pathDownload.mkdirs();
		final var classLoader = Mockito.mock(LigojPluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(pathDownload.toPath());
		configuration.put("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		// Index
		try (var inputStream = new ClassPathResource("mock-server/prov/terraform/terraform-index.html")
				.getInputStream()) {
			httpServer.stubFor(get(urlEqualTo("/"))
					.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toByteArray(inputStream))));
		}

		// ZIP file
		try (var inputStream = new ClassPathResource(file).getInputStream()) {
			httpServer.stubFor(get(urlEqualTo("/0.11.5/terraform_0.11.5_linux_amd64.zip"))
					.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toByteArray(inputStream))));
		}

		httpServer.start();
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected LigojPluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			protected String getCurrentOs() {
				return "linux";
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(utils);

		// Remove state files
		FileUtils.deleteQuietly(new File(pathDownload, "prov/terraform"));
		FileUtils.deleteQuietly(new File(pathDownload, "prov/terraform.exe"));

		return utils;
	}

	@Test
	void getLatestVersion() {
		final var version = resource.getLatestVersion();
		Assertions.assertEquals(3, StringUtils.split(StringUtils.trimToEmpty(version), '.').length);
	}

	@Test
	void getLatestVersionMock() throws IOException {
		configuration.put("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		// Index
		try (var inputStream = new ClassPathResource("mock-server/prov/terraform/terraform-index.html")
				.getInputStream()) {
			httpServer.stubFor(get(urlEqualTo("/"))
					.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toByteArray(inputStream))));
		}
		httpServer.start();
		Assertions.assertEquals("0.11.5", resource.getLatestVersion());
	}

	@Test
	void getLatestVersionNotAvailable() {
		configuration.put("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		Assertions.assertNull(resource.getLatestVersion());
	}

	@Test
	void toFile() throws IOException {
		final var classLoader = Mockito.mock(LigojPluginsClassLoader.class);
		try (var scope = new ThreadClassLoaderScope(new URLClassLoader(new URL[0], classLoader))) {
			final var file = Paths.get("");
			final var entity = new Subscription();
			entity.setId(15);
			Mockito.when(classLoader.toPath(entity, "some")).thenReturn(file);
			Assertions.assertEquals(file.toFile(), resource.toFile(entity, "some"));
			Assertions.assertNotNull(LigojPluginsClassLoader.getInstance());
		}
	}

	@Test
	void zipUnzip() throws IOException {
		final var from = new File("target/test-classes/terraform-zip-unzip").toPath();
		final var toZip = new File("target/test-classes/terraform-out.zip");
		FileUtils.deleteQuietly(toZip);
		try (var toStream = new FileOutputStream(toZip)) {
			resource.zip(from, toStream);
		}
		final var to = new File("target/test-classes/terraform-out");
		FileUtils.deleteQuietly(to);
		FileUtils.forceMkdir(to);
		try (var fromStream = new FileInputStream(toZip)) {
			resource.unzip(fromStream, to);
		}
		Assertions.assertEquals("var=\"bar1\"",
				FileUtils.readFileToString(new File(to, "module/bar1.tf"), StandardCharsets.UTF_8));
		Assertions.assertEquals("var=\"bar0\"",
				FileUtils.readFileToString(new File(to, "bar0.tf"), StandardCharsets.UTF_8));
		Assertions.assertFalse(new File(to, "plan.ptf").exists());
		Assertions.assertFalse(new File(to, "secrets.auto.tfvars").exists());
		Assertions.assertFalse(new File(to, ".terraform/foo.tf").exists());
	}

	@Test
	void addEntryFailed() throws IOException {
		final var from = new File("target/test-classes/terraform-logs", "init.log").toPath();
		final var zs = Mockito.mock(ZipOutputStream.class);
		Mockito.doThrow(new IOException()).when(zs).putNextEntry(ArgumentMatchers.any());
		resource.addEntry(new File("target/test-classes/terraform-logs").toPath(), from, zs);
	}
}
