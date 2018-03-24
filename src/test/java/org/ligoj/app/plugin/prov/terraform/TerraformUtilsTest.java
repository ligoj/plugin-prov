package org.ligoj.app.plugin.prov.terraform;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipException;

import javax.transaction.Transactional;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
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
public class TerraformUtilsTest extends AbstractServerTest {

	private static final File MOCK_PATH = new File("target/test-classes/terraform-test").getAbsoluteFile();

	private static final File EMPTY_PATH = new File("target/test-classes/terraform-empty").getAbsoluteFile();

	@Autowired
	private TerraformUtils resource;

	@Autowired
	private ConfigurationResource configuration;

	@Autowired
	private CacheManager cacheManager;

	@BeforeEach
	public void reset() {
		cacheManager.getCache("configuration").clear();
	}

	@Test
	public void getCurrentOs() {
		Assertions.assertNotNull(resource.getCurrentOs());
	}

	@Test
	public void getOsValue() {
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "UNKNOWN"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD 1.0"));
		Assertions.assertEquals("terraform", resource.getOsValue(resource.bins, "FreeBSD 1.0 A"));
		Assertions.assertEquals("terraform.exe", resource.getOsValue(resource.bins, "Windows 2012"));
	}

	// Coverage only
	@Test
	public void getClassLoader() {
		Assertions.assertNull(resource.getClassLoader());
		Assertions.assertNotNull(resource.getCurrentOs());
	}

	@Test
	public void newBuilderOther() {
		checkNewBuilderBsd("UNKNOWN");
	}

	@Test
	public void newBuilderBsd() {
		checkNewBuilderBsd("FreeBSD");
	}

	@Test
	public void newBuilderBsd2() {
		checkNewBuilderBsd("FreeBSD 1.0");
	}

	@Test
	public void newBuilderBsd3() {
		checkNewBuilderBsd("FreeBSD 1.0 A");
	}

	public void checkNewBuilderBsd(final String os) {
		checkNewBuilder(os, new String[] { "sh", "-c", "terraform arg1 arg2" });
	}

	@Test
	public void newBuilderWindows() {
		checkNewBuilder("Windows Server 2012", new String[] { "cmd.exe", "/c", "terraform.exe arg1 arg2" });
	}

	@Test
	public void newBuilderNotInstalled() {
		final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(EMPTY_PATH.toPath());
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected PluginsClassLoader getClassLoader() {
				return classLoader;
			}

		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(utils);
		Assertions.assertEquals("terraform-not-found",
				Assertions.assertThrows(BusinessException.class, () -> utils.newBuilder("arg1", "arg2")).getMessage());
	}

	private void checkNewBuilder(final String os, final String[] args) {
		final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(MOCK_PATH.toPath());
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected PluginsClassLoader getClassLoader() {
				return classLoader;
			}

			@Override
			protected String getCurrentOs() {
				return os;
			}
		};
		applicationContext.getAutowireCapableBeanFactory().autowireBean(utils);
		final ProcessBuilder newBuilder = utils.newBuilder("arg1", "arg2");
		Assertions.assertEquals(Arrays.asList(args), newBuilder.command());
	}

	@Test
	public void install() throws IOException {
		final TerraformUtils utils = prepareForInstall("mock-server/prov/terraform/terraform.zip");
		utils.install();

		// Check file exists and runnable
		final File pathDownload = new File("target/test-classes/terraform-download").getAbsoluteFile();
		final File file;
		if (SystemUtils.IS_OS_WINDOWS) {
			file = new File(pathDownload, "prov/terraform.exe");
		} else {
			file = new File(pathDownload, "prov/terraform");
		}
		Assertions.assertTrue(file.exists());
		Assertions.assertTrue(IOUtils.toString(new FileInputStream(file), "UTF-8").startsWith("#EMPTY"));
	}

	@Test
	public void installInvalidZip() throws IOException {
		final TerraformUtils utils = prepareForInstall("mock-server/prov/terraform/terraform-invalid.zip");
		Assertions.assertThrows(ZipException.class, () -> utils.install());
	}

	private TerraformUtils prepareForInstall(final String file) throws IOException {

		// Prepare the download
		final File pathDownload = new File("target/test-classes/terraform-download").getAbsoluteFile();
		pathDownload.mkdirs();
		final PluginsClassLoader classLoader = Mockito.mock(PluginsClassLoader.class);
		Mockito.when(classLoader.getHomeDirectory()).thenReturn(pathDownload.toPath());
		configuration.saveOrUpdate("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		// Index
		httpServer.stubFor(
				get(urlEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toByteArray(
						new ClassPathResource("mock-server/prov/terraform/terraform-index.html").getInputStream()))));
		// ZIP file
		httpServer.stubFor(get(urlEqualTo("/0.11.5/terraform_0.11.5_linux_amd64.zip"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK)
						.withBody(IOUtils.toByteArray(new ClassPathResource(file).getInputStream()))));
		httpServer.start();
		final TerraformUtils utils = new TerraformUtils() {
			@Override
			protected PluginsClassLoader getClassLoader() {
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
	public void getLastestVersion() {
		final String version = resource.getLastestVersion();
		Assertions.assertEquals(3, StringUtils.split(version, '.').length);
	}

	@Test
	public void getLastestVersionMock() throws IOException {
		configuration.saveOrUpdate("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		// Index
		httpServer.stubFor(
				get(urlEqualTo("/")).willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toByteArray(
						new ClassPathResource("mock-server/prov/terraform/terraform-index.html").getInputStream()))));
		httpServer.start();
		Assertions.assertEquals("0.11.5", resource.getLastestVersion());
	}

	@Test
	public void getLastestVersionNotAvailable() {
		configuration.saveOrUpdate("service:prov:terraform:repository", "http://localhost:" + MOCK_PORT);
		Assertions.assertEquals("terraform-lastest-version",
				Assertions.assertThrows(BusinessException.class, () -> resource.getLastestVersion()).getMessage());
	}
}
