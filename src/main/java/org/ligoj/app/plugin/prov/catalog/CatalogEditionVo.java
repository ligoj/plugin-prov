package org.ligoj.app.plugin.prov.catalog;

import org.ligoj.app.api.NodeVo;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvLocation;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CatalogEditionVo {
	
	private Integer preferredLocation;
	
	private Integer node;
}
