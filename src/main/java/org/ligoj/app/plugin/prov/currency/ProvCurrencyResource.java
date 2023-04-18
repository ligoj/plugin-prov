/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.currency;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

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
	 * @param id The currency identifier.
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
	 * @param entity The entity to save.
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
	 * @param entity The entity to save.
	 */
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void update(final ProvCurrency entity) {
		repository.saveAndFlush(entity);
	}

	/**
	 * Return the currency configurations.
	 *
	 * @param uriInfo filter data.
	 * @return The filtered currency configurations.
	 */
	@GET
	@Consumes(MediaType.APPLICATION_JSON)
	public TableItem<CurrencyVo> findAll(@Context final UriInfo uriInfo) {
		return paginationJson.applyPagination(uriInfo, repository.findAll(DataTableAttributes.getSearch(uriInfo),
				paginationJson.getPageRequest(uriInfo, ORM_COLUMNS)), Function.identity());
	}

}
