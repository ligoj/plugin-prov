/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import java.util.List;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.dao.ProvQuoteViewRepository;
import org.ligoj.app.plugin.prov.model.ProvQuoteView;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.Getter;
import lombok.Setter;

/**
 * Shared quote views — named captures of the quote screen state stored per subscription and visible to every user of
 * that subscription. The view body is an opaque JSON document owned by the UI; the backend only names, stores and
 * scopes it. Saving an existing name replaces it (upsert), mirroring the browser-local views' behavior.
 */
@Service
@Path(ProvResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class ProvQuoteViewResource {

	/**
	 * Edition payload: the view name and its serialized state.
	 */
	@Getter
	@Setter
	public static class QuoteViewEditionVo {
		private String name;
		private String description;
		private String data;
	}

	@Autowired
	protected SubscriptionResource subscriptionResource;

	@Autowired
	private ProvQuoteViewRepository repository;

	@Autowired
	private ProvResource resource;

	/**
	 * Return the shared views of a subscription, sorted by name.
	 *
	 * @param subscription The subscription identifier.
	 * @return The shared views (with their document — views are small).
	 */
	@GET
	@Path("{subscription:\\d+}/view")
	public List<ProvQuoteView> findAll(@PathParam("subscription") final int subscription) {
		subscriptionResource.checkVisible(subscription);
		return repository.findAllBySubscriptionIdOrderByNameAsc(subscription);
	}

	/**
	 * Create or replace (by name) a shared view.
	 *
	 * @param subscription The subscription identifier.
	 * @param vo           The view name + serialized state.
	 * @return The view identifier.
	 */
	@POST
	@Path("{subscription:\\d+}/view")
	public int save(@PathParam("subscription") final int subscription, final QuoteViewEditionVo vo) {
		subscriptionResource.checkVisible(subscription);
		final var entity = java.util.Optional
				.ofNullable(repository.findBySubscriptionIdAndName(subscription, vo.getName()))
				.orElseGet(ProvQuoteView::new);
		entity.setName(vo.getName());
		entity.setDescription(vo.getDescription());
		entity.setData(vo.getData());
		entity.setSubscription(resource.getQuoteFromSubscription(subscription).getSubscription());
		repository.saveAndFlush(entity);
		return entity.getId();
	}

	/**
	 * Delete a shared view.
	 *
	 * @param subscription The subscription identifier.
	 * @param id           The view identifier.
	 */
	@DELETE
	@Path("{subscription:\\d+}/view/{id:\\d+}")
	public void delete(@PathParam("subscription") final int subscription, @PathParam("id") final int id) {
		subscriptionResource.checkVisible(subscription);
		final var entity = repository.findOneExpected(id);
		if (entity.getSubscription().getId() != subscription) {
			throw new EntityNotFoundException(String.valueOf(id));
		}
		repository.delete(entity);
	}
}
