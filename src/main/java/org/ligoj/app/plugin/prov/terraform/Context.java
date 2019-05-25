/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.terraform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A context holder.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Context {

	@JsonIgnore
	private Subscription subscription;
	@JsonIgnore
	private QuoteVo quote;
	private String location;
	@JsonIgnore
	private List<String[]> sequence;
	@JsonIgnore
	private Map<InstanceMode, List<ProvQuoteInstance>> modes;
	@JsonIgnore
	private List<ProvQuoteInstance> instances;
	private Map<String, String> context = new HashMap<>();

	/**
	 * Add a value to the context.
	 * 
	 * @param key   Key of the context.
	 * @param value Value of the context.
	 * @return This object.
	 */
	public Context add(final String key, final String value) {
		context.put(key, value);
		return this;
	}

	/**
	 * Return the context value. Is a shorthand of
	 * <code> getContext().get(key)</code>
	 * 
	 * @param key The context key.
	 * @return The context value.
	 */
	public String get(final String key) {
		return context.get(key);
	}
}
