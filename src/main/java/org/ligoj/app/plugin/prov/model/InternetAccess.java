package org.ligoj.app.plugin.prov.model;

/**
 * Internet access options.
 */
public enum InternetAccess {

	/**
	 * Internet facing
	 */
	PUBLIC, 
	
	/**
	 * Private network without Internet access.
	 */
	PRIVATE, 
	
	/**
	 * Private network with Internet access with NAT.
	 */
	PRIVATE_NAT
}
