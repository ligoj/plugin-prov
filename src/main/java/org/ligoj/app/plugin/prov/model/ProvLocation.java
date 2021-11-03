/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.ligoj.app.model.Node;
import org.ligoj.bootstrap.core.model.AbstractDescribedEntity;
import org.springframework.lang.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Getter;
import lombok.Setter;

/**
 * Location for a VM provider. The name attribute is an unique location API name within the VM provider.
 *
 * @see <a href="https://unstats.un.org/unsd/methodology/m49/">unstats.un.org</a>
 * @see <a href="https://en.wikipedia.org/wiki/UN_M.49">UN_M.49</a>
 */
@Getter
@Setter
@Entity
@Table(name = "LIGOJ_PROV_LOCATION", uniqueConstraints = @UniqueConstraint(columnNames = { "name", "node" }))
public class ProvLocation extends AbstractDescribedEntity<Integer> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * The related node (VM provider) of this location.
	 */
	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JsonIgnore
	private Node node;

	/**
	 * Longitude of the data center.
	 */
	private Double longitude;

	/**
	 * Latitude of the data center
	 */
	private Double latitude;

	/**
	 * Region or city name inside the country. Values: Florida, Sydney, Beijing, Paris, ...
	 */
	private String subRegion;

	/**
	 * Placement inside the country, not at region or continent level. Values : north, west, east, south, central,
	 * northwest,...
	 */
	private String placement;

	/**
	 * M49 country code: 840 (USA), 250 (France), ...
	 */
	private Integer countryM49;

	/**
	 * ISO 3166-1-alpha-2 country code: US (USA), FR (France), ...
	 */
	private String countryA2;

	/**
	 * M49 region code: 154 (Northern Europe), 021 (Northern America), 155 (Western Europe)...
	 */
	private Integer regionM49;

	/**
	 * M49 continent code: 142 (Asia), 150 (Europe), 019 (Americas),...
	 */
	private Integer continentM49;
	
	/**
	 * When true, this location is the default one for new quotes
	 */
	private  boolean preferred;

}
