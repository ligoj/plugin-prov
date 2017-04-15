package org.ligoj.app.plugin.prov;

import java.util.List;

import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;

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
	private ProvQuoteInstance instance;

	/**
	 * Storages of this VM.
	 */
	private List<ProvQuoteStorage> storages;
}
