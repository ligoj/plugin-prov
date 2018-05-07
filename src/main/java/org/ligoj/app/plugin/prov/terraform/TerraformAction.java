package org.ligoj.app.plugin.prov.terraform;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Terraform executor.
 */
public interface TerraformAction {

	/**
	 * Execute a Terraform action. Can either be a real Terraform action, either a bean's function.
	 *
	 * @param context
	 *            The Terraform user inputs. Will be completed and passed to Terraform commands.
	 * @param out
	 *            The target log outputs.
	 * @param arguments
	 *            The Terraform arguments passed to the executable.
	 * @throws InterruptedException
	 *             When the execution is interrupted.
	 * @throws IOException
	 *             When logs cannot be written.
	 */
	void execute(Context context, OutputStream out, String... arguments) throws IOException, InterruptedException;
}
