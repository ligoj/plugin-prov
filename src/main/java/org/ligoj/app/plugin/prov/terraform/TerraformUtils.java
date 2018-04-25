package org.ligoj.app.plugin.prov.terraform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.cache.annotation.CacheResult;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ligoj.app.model.Subscription;
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

	@Autowired
	protected ConfigurationResource configuration;

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
	 * Return the Terraform commands.
	 * 
	 * @return The Terraform commands. Each command correspond to a list of Terraform arguments. The first argument
	 *         corresponds to the Terraform command name.
	 * @see <a href="https://www.terraform.io/docs/commands/init.html">plan</a>
	 * @see <a href="https://www.terraform.io/docs/commands/plan.html">plan</a>
	 * @see <a href="https://www.terraform.io/docs/commands/show.html">show</a>
	 * @see <a href="https://www.terraform.io/docs/commands/apply.html">apply</a>
	 * @see <a href="https://www.terraform.io/docs/commands/destroy.html">apply</a>
	 */
	public List<String[]> getTerraformCommands() {
		return Arrays.stream(getTerraformSequence()).map(String::trim)
				.map(step -> configuration.get("service:prov:terraform:command-" + step, "-v"))
				.map(step -> step.split(" ")).collect(Collectors.toList());
	}

	/**
	 * Return the Terraform command names such as <code>init,plan</code>.
	 * 
	 * @return The Terraform command names such as <code>init,plan</code>.
	 */
	public String[] getTerraformSequence() {
		return configuration.get("service:prov:terraform:sequence", "init").split(",");
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
		try (InputStream openStream = new URL(url).openStream()) {
			unzip(toDir, openStream).stream().forEach(f -> f.setExecutable(true));
		}
	}

	/**
	 * Zip all files from the given path to the given outputStream. Includes all files but secret variable files and
	 * <code>.terraform</code>.
	 * 
	 * @param subscription
	 *            The source subscription.
	 * @param out
	 *            The target ZIP file stream.
	 * @return The compressed files.
	 * @throws IOException
	 *             When zip fails : download, unzip, write file,...
	 */
	public List<File> zip(final Subscription subscription, final OutputStream out) throws IOException {
		return zip(toFile(subscription).toPath(), out);
	}

	/**
	 * Zip all files from the given path to the given outputStream. Includes all files but secret variable files and
	 * <code>.terraform</code>.
	 * 
	 * @param fromDir
	 *            The source directory containing the files.
	 * @param out
	 *            The target ZIP file stream.
	 * @return The compressed files.
	 * @throws IOException
	 *             When zip fails : download, unzip, write file,...
	 */
	public List<File> zip(final java.nio.file.Path fromDir, final OutputStream out) throws IOException {
		final List<File> files = new ArrayList<>();
		try (ZipOutputStream zs = new ZipOutputStream(out)) {
			Files.walk(fromDir).filter(path -> !Files.isDirectory(path))
					.filter(path -> !StringUtils.endsWithAny(path.toString(), ".ptf", "secrets.auto.tfvars"))
					.filter(path -> !path.toString().contains(".terraform")).forEach(path -> {
						// Excludes ".terraform", secrets, and "*.ptf" files
						final ZipEntry zipEntry = new ZipEntry(fromDir.relativize(path).toString());
						try {
							zs.putNextEntry(zipEntry);
							Files.copy(path, zs);
							zs.closeEntry();
							files.add(path.toFile());
						} catch (IOException e) {
							System.err.println(e);
						}
					});
		}
		return files;
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
		final List<File> files = new ArrayList<>();
		try (ZipInputStream zis = new ZipInputStream(source)) {
			FileUtils.forceMkdir(toDir);
			ZipEntry zipEntry = zis.getNextEntry();
			while (zipEntry != null) {
				final File file = new File(toDir, zipEntry.getName());
				files.add(file);
				FileUtils.forceMkdirParent(file);
				final FileOutputStream fos = new FileOutputStream(file);
				zis.transferTo(fos);
				fos.close();
				zipEntry = zis.getNextEntry();
			}
		}
		return files;
	}

	/**
	 * Return the file reference from the given subscription. The file will relative to the related subscription.
	 * 
	 * @param subscription
	 *            The subscription related to this operation.
	 * @param fragments
	 *            The requested sub path fragments.
	 * @return The Terraform resource file scoped by the given subscription.
	 */
	public File toFile(final Subscription subscription, final String... fragments) throws IOException {
		return PluginsClassLoader.getInstance().toPath(subscription, fragments).toFile();
	}
}
