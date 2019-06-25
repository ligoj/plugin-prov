package org.ligoj.app.plugin.prov.quote.support;

import java.util.List;

import org.ligoj.app.plugin.prov.TagVo;

/**
 * Tag property wrapper.
 */
public interface QuoteTagSupport {

	/**
	 * Return the tags to override.
	 * 
	 * @return The tags to override when not <code>null</code>. May be <code>null</code>
	 */
	List<TagVo> getTags();

	/**
	 * Set the tags to override.
	 * 
	 * @param newTags The tags to override when not <code>null</code>. May be <code>null</code>
	 */
	void setTags(List<TagVo> newTags);

}
