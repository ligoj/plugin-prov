/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.springframework.stereotype.Service;

@Service
@Path(TstProvPluginResource.URL)
@Produces(MediaType.APPLICATION_JSON)
class TstProvPluginResource extends AbstractProvResource {

	/**
	 * Plug-in key.
	 */
	public static final String URL = ProvResource.SERVICE_URL + "/test";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = URL.replace('/', ':').substring(1);

	@Override
	public String getKey() {
		return KEY;
	}

}
