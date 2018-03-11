package org.ligoj.app.plugin.prov.terraform;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
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
	 * Terraform base command with argument. The Terraform binary must be in the PATH.
	 */
	private static final String[] TERRAFORM_COMMAND_WIN = { "cmd.exe", "/c" };
	private static final String[] TERRAFORM_COMMAND_LINUX = { "sh", "-c" };
	private static final String[] TERRAFORM_COMMAND = SystemUtils.IS_OS_WINDOWS ? TERRAFORM_COMMAND_WIN
			: TERRAFORM_COMMAND_LINUX;

	@Autowired
	protected ConfigurationResource configuration;

	/**
	 * A new {@link ProcessBuilder} with the given arguments
	 * 
	 * @param The
	 *            process arguments.
	 * @param The
	 *            new {@link ProcessBuilder} instance.
	 */
	protected ProcessBuilder newBuilder(String... args) {
		return new ProcessBuilder(ArrayUtils.addAll(TERRAFORM_COMMAND,
				configuration.get(TERRAFORM_PATH, "terraform") + " " + StringUtils.join(ArrayUtils.addAll(args), ' ')));
	}

	/**
	 * Return the Terraform sequence with step names.
	 * 
	 * @return The Terraform sequence with step names.
	 */
	protected String[][] getTerraformSequence() {
		return new String[][] { { "plan", NO_INPUT, NO_COLOR, "-detailed-exitcode" }, { "apply", NO_INPUT, NO_COLOR },
				{ "show", NO_INPUT, NO_COLOR } };
	}

}
