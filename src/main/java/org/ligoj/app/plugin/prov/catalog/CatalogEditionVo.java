package org.ligoj.app.plugin.prov.catalog;

import org.ligoj.app.api.NodeVo;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvLocation;

import com.sun.istack.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEditionVo {

	@NotNull
	private Integer preferredLocation;

	@NotNull
	private String node;
}
