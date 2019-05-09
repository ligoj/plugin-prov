/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.currency;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.dao.ProvCurrencyRepository;
import org.ligoj.app.plugin.prov.model.CurrencyVo;
import org.ligoj.app.plugin.prov.model.ProvCurrency;
import org.ligoj.bootstrap.core.json.PaginationJson;
import org.ligoj.bootstrap.core.json.TableItem;
import org.ligoj.bootstrap.core.json.datatable.DataTableAttributes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The currency management.
 */
@Service
@Path(ProvResource.SERVICE_URL + "/currency")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvCurrencyResource {

	/**
	 * Ordered/mapped columns.
	 */
	private static final Map<String, String> ORM_COLUMNS = new HashMap<>();

	@Autowired
	private ProvCurrencyRepository repository;

	@Autowired
	private PaginationJson paginationJson;

	static {
		ORM_COLUMNS.put("name", "name");
		ORM_COLUMNS.put("description", "description");
		ORM_COLUMNS.put("unit", "unit");
	}

	/**
	 * Delete the given currency. It must not be used.
	 *
	 * @param id
	 *            The currency identifier.
	 */
	@DELETE
	@Path("{id:\\d+}")
	@Consumes(MediaType.APPLICATION_JSON)
	public void delete(@PathParam("id") final int id) {
		repository.deleteNoFetch(id);
	}

	/**
	 * Create the entity.
	 *
	 * @param entity
	 *            The entity to save.
	 * @return The created entity's identifier.
	 */
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	public int create(final ProvCurrency entity) {
		return repository.saveAndFlush(entity).getId();
	}

	/**
	 * Update the entity.
	 *
	 * @param entity
	 *            The entity to save.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(final ProvCurrency entity) {
		repository.saveAndFlush(entity);
	}

	/**
	 * Return the currency configurations.
	 *
	 * @param uriInfo
	 *            filter data.
	 * @return The filtered currency configurations.
	 */
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<CurrencyVo> findAll(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo, repository.findAll(DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
	}

}
