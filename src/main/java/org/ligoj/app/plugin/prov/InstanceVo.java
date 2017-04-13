package org.ligoj.app.plugin.prov;

import java.util.List;

import org.ligoj.app.plugin.prov.model.QuoteInstance;
import org.ligoj.app.plugin.prov.model.QuoteStorage;

import lombok.Getter;
import lombok.Setter;

/**
 * An instance within a quote. Includes attached storage.
 */
@Getter
@Setter
public class InstanceVo {

	/**
	 * The instance type with pricing options.
	 */
	private QuoteInstance instance;

	/**
	 * Storages of this VM.
	 */
	private List<QuoteStorage> storages;
}
