/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov;

import org.ligoj.app.plugin.prov.model.AbstractInstanceType;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVm;
import org.ligoj.app.plugin.prov.model.AbstractQuoteVmOs;
import org.ligoj.app.plugin.prov.model.AbstractTermPriceVmOs;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.QuoteVm;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.validation.ValidationJsonException;

import lombok.extern.slf4j.Slf4j;

/**
 * The resource part of the provisioning.
 *
 * @param <T> The instance resource type.
 * @param <P> Quoted resource price type.
 * @param <C> Quoted resource type.
 * @param <E> Quoted resource edition VO type.
 * @param <L> Quoted resource lookup result type.
 * @param <Q> Quoted resource details type.
 */
@Slf4j
public abstract class AbstractProvQuoteInstanceOsResource<T extends AbstractInstanceType, P extends AbstractTermPriceVmOs<T>, C extends AbstractQuoteVm<P>, E extends AbstractQuoteInstanceEditionVo, L extends AbstractLookup<P>, Q extends QuoteVm>
		extends AbstractProvQuoteInstanceResource<T, P, C, E, L, Q> {

	/**
	 * Check the requested OS is compliant with the one of associated {@link ProvInstancePrice}
	 * 
	 * @param entity The instance to check.
	 */
	protected void checkOs(final AbstractQuoteVmOs<P> entity) {
		final var service = getService(entity.getConfiguration());
		if (service.getCatalogOs(entity.getOs()) != entity.getPrice().getOs()) {
			// Incompatible, hack attempt?
			log.warn("Attempt to create an instance with an incompatible OS {} with catalog OS {}", entity.getOs(),
					entity.getPrice().getOs());
			throw new ValidationJsonException("os", "incompatible-os", entity.getPrice().getOs());
		}
	}

	/**
	 * Return <code>true</code> if the current OS accept BYOL.
	 * 
	 * @param os The OS to evaluate.
	 * @return <code>true</code> if the current OS accept BYOL.
	 */
	protected boolean canByol(final VmOs os) {
		return os == VmOs.WINDOWS;
	}

}
