package org.ligoj.app.plugin.prov.catalog;

import com.sun.istack.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogEditionVo {

	@NotNull
	private Integer preferredLocation;

	@NotNull
	private String node;
}
