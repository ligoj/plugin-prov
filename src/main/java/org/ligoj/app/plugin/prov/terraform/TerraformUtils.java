package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.cache.annotation.CacheResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ligoj.app.resource.plugin.CurlProcessor;
import org.ligoj.app.resource.plugin.PluginsClassLoader;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Terraform utilities.
 */
@Component
public class TerraformUtils {

	/**
	 * Tarraform fag to disable interactive mode
	 */
	private static final String NO_INPUT = "-input=false";

	/**
	 * Tarraform fag to disable color mode
	 */
	private static final String NO_COLOR = "-no-color";

	/**
	 * Configuration key for Terraform command path
	 */
	private static final String TERRAFORM_PATH = "terraform.path";

	/**
	 * Default base repository URL composed by <code>%V</code> for the version and <code>%D</code> for the distribution.
	 * 
	 * @see a href="https://releases.hashicorp.com/terraform/0.11.5/terraform_0.11.5_linux_amd64.zip">0.11.5 on
	 *      Linux></a>
	 */
	private static final String BASE_REPO = "https://releases.hashicorp.com/terraform/";

	/**
	 * Configuration key for Terraform base repository URL.
	 */
	private static final String CONF_REPO = "service:prov:terraform:repository";

	/**
	 * Pattern to detect the version inside the release index file.
	 */
	private static final Pattern VERSION_PATTERN = Pattern.compile("<a href=\"/terraform/([^/]+)/\">terraform_");

	/**
	 * Terraform base command with argument. The Terraform binary must be in the PATH.
	 */
	private static final String[] TERRAFORM_SHELL_WIN = { "cmd.exe", "/c" };
	private static final String[] TERRAFORM_SHELL_LINUX = { "sh", "-c" };
	protected Map<String, String[]> shells = new HashMap<>();

	/**
	 * Terraform binary file.
	 */
	protected Map<String, String> bins = new HashMap<>();

	/**
	 * Hashicorp distributions.
	 * 
	 * @see <a heref="https://www.terraform.io/downloads.html">terraform</a>
	 */
	protected Map<String, String> distributions = new HashMap<>();

	@Autowired
	protected ConfigurationResource configuration;

	public TerraformUtils() {
		shells.put("windows", TERRAFORM_SHELL_WIN);
		shells.put("default", TERRAFORM_SHELL_LINUX);
		distributions.put("windows", "windows_amd64");
		distributions.put("mac", "darwin_amd64");
		distributions.put("freebsd", "freebsd_amd64");
		distributions.put("solaris", "solaris_amd64");
		distributions.put("openbsd", "openbsd_amd64");
		distributions.put("default", "linux_amd64");
		bins.put("windows", "terraform.exe");
		bins.put("default", "terraform");
	}

	/**
	 * A new {@link ProcessBuilder} with the given arguments
	 * 
	 * @param The
	 *            process arguments.
	 * @param The
	 *            new {@link ProcessBuilder} instance.
	 */
	public ProcessBuilder newBuilder(final String... args) {
		// Check Terraform is available
		if (!isInstalled()) {
			throw new BusinessException("terraform-not-found");
		}
		final String[] shell = getOsValue(shells);
		final String bin = getHome().resolve(configuration.get(TERRAFORM_PATH, getOsValue(bins))).toString();
		return new ProcessBuilder(ArrayUtils.addAll(shell, bin + " " + StringUtils.join(ArrayUtils.addAll(args), ' ')));
	}

	/**
	 * Return the map value associated to the closest key of given OS. For sample, when current OS is
	 * <code>Windows 10</code>, the match with use <code>windows 10</code>, then <code>windows</code>, then
	 * <code>default</code>, then <code>null</code>.
	 * 
	 * @param map
	 *            The OS mapping.
	 * @return The map value associated to the closest key of given OS. <code>null</code> when not found.
	 */
	protected <T> T getOsValue(final Map<String, T> map) {
		return getOsValue(map, getCurrentOs());
	}

	/**
	 * Return the map value associated to the closest key of given OS. For sample, when <param>os</param> is
	 * <code>Windows 10</code>, the match with use <code>windows 10</code>, then <code>windows</code>, then
	 * <code>default</code>, then <code>null</code>.
	 * 
	 * @param map
	 *            The OS mapping.
	 * @param os
	 *            The OS to search.
	 * @return The map value associated to the closest key of given OS. <code>null</code> when not found.
	 */
	protected <T> T getOsValue(final Map<String, T> map, final String os) {
		final String[] osParts = StringUtils.trimToEmpty(os).toLowerCase(Locale.ENGLISH).split(" ");
		return IntStream.iterate(osParts.length, i -> --i).limit(osParts.length)
				.mapToObj(i -> map.get(StringUtils.join(osParts, " ", 0, i))).filter(Objects::nonNull).findFirst()
				.orElseGet(() -> map.get("default"));
	}

	/**
	 * Return the Terraform sequence with step names.
	 * 
	 * @return The Terraform sequence with step names.
	 */
	public String[][] getTerraformSequence() {
		return new String[][] { { "plan", NO_INPUT, NO_COLOR, "-detailed-exitcode" }, { "apply", NO_INPUT, NO_COLOR },
				{ "show", NO_INPUT, NO_COLOR } };
	}

	/**
	 * Return the provisioning home.
	 * 
	 * @return The provisioning home path.
	 */
	protected Path getHome() {
		return getClassLoader().getHomeDirectory().resolve("prov");
	}

	/**
	 * Return the plugin class loader if available.
	 * 
	 * @return The provisioning home path.
	 */
	protected PluginsClassLoader getClassLoader() {
		return PluginsClassLoader.getInstance();
	}

	/**
	 * Return the current OS name.
	 * 
	 * @return The current OS name.
	 */
	protected String getCurrentOs() {
		return SystemUtils.OS_NAME;
	}

	/**
	 * Return the Terraform binary file reference.
	 */
	private Path getTerraformBin() {
		return getHome().resolve(getOsValue(bins));
	}

	/**
	 * Indicates the Terraform binary exists.
	 * 
	 * @return <code>true</code> when the Terraform binary exists.
	 */
	protected boolean isInstalled() {
		return getTerraformBin().toFile().exists();
	}

	/**
	 * Return the latest available version from the repository.
	 * 
	 * @return The latest available version from the repository. <code>null</code> when undefined.
	 */
	@CacheResult(cacheName = "terraform-version-latest")
	public String getLastestVersion() {
		final Matcher matcher = VERSION_PATTERN.matcher(
				StringUtils.defaultString(new CurlProcessor().get(configuration.get(CONF_REPO, BASE_REPO)), ""));
		if (matcher.find()) {
			// Version has been found
			return matcher.group(1);
		}

		// Unable to detect the latest version from the index
		return null;
	}

	/**
	 * Install latest version of Terraform in the current system using the default repository and the home directory. It
	 * override the previous version :
	 * <ul>
	 * <li>Determine the last release</li>
	 * <li>Determine the right repository</li>
	 * <li>Determine the right distribution</li>
	 * <li>Download</li>
	 * <li>Unzip</li>
	 * <li>Check the version</li>
	 * </ul>
	 * 
	 * @throws IOException
	 *             When unzip fails : download, unzip, write file,...
	 */
	public void install() throws IOException {
		install(getLastestVersion());
	}

	/**
	 * Install latest version of Terraform in the given directory. It override the previous version :
	 * <ul>
	 * <li>Determine the right repository</li>
	 * <li>Determine the right distribution</li>
	 * <li>Download</li>
	 * <li>Unzip</li>
	 * <li>Check the version</li>
	 * </ul>
	 * 
	 * @param version
	 *            The target version to install.
	 * @throws IOException
	 *             When unzip fails : download, unzip, write file,...
	 */
	public void install(final String version) throws IOException {
		install(getHome().toFile(), configuration.get(CONF_REPO, BASE_REPO), version);
	}

	/**
	 * Install latest version of Terraform in the current system using the default repository :
	 * <ul>
	 * <li>Determine the right distribution</li>
	 * <li>Download</li>
	 * <li>Unzip</li>
	 * <li>Check the version</li>
	 * </ul>
	 * 
	 * @param toDir
	 *            The target directory where Terraform will be installed.
	 * @param repository
	 *            The target binary repository.
	 * @param version
	 *            The target version to install.
	 * @throws IOException
	 *             When unzip fails : download, unzip, write file,...
	 */
	private void install(final File toDir, final String repository, final String version) throws IOException {
		install(toDir, StringUtils.appendIfMissing(repository, "/") + version + "/terraform_" + version + "_"
				+ getOsValue(distributions) + ".zip");
	}

	/**
	 * Install Terraform binary in the current system :
	 * <ul>
	 * <li>Download</li>
	 * <li>Unzip</li>
	 * <li>Check the version</li>
	 * </ul>
	 * 
	 * @param toDir
	 *            The target directory where Terraform will be installed.
	 * @param url
	 *            The URL to download.
	 * @throws IOException
	 *             When unzip fails : download, unzip, write file,...
	 */
	private void install(final File toDir, final String url) throws IOException {
		unzip(toDir, new URL(url).openStream()).stream().forEach(f -> f.setExecutable(true));
	}

	/**
	 * Unzip all files from the given ZIP stream to target directory and return the unziped files.
	 * 
	 * @param toDir
	 *            The target directory where uncompressed files will be placed.
	 * @param source
	 *            The source ZIP file stream.
	 * @return The uncompressed files.
	 * @throws IOException
	 *             When unzip fails : download, unzip, write file,...
	 */
	public List<File> unzip(final File toDir, final InputStream source) throws IOException {
		ZipInputStream zis = null;
		final List<File> files = new ArrayList<>();
		try {
			zis = new ZipInputStream(source);
			FileUtils.forceMkdir(toDir);
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				final File file = new File(toDir, zipEntry.getName());
				files.add(file);
				final FileOutputStream fos = new FileOutputStream(file);
				zis.transferTo(fos);
				fos.close();
				zipEntry = zis.getNextEntry();
			}
		} finally {
			IOUtils.closeQuietly(source);
			IOUtils.closeQuietly(zis);
		}
		return files;
	}
}
