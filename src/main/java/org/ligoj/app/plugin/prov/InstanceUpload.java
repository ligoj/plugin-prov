package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.ProvStorageFrequency;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.VmOs;

import lombok.Getter;
import lombok.Setter;

/**
 * CSV data import type
 */
@Getter
@Setter
public class InstanceUpload {
	private String name;
	private Double cpu;
	private Double ram;
	private Boolean constant;
	private VmOs os;
	private Double disk;
	private ProvStorageFrequency frequency;
	private ProvStorageOptimized optimized;
	private String instance;
	private String priceType;

}
