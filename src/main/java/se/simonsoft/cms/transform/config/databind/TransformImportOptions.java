package se.simonsoft.cms.transform.config.databind;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // Allow future changes.
public class TransformImportOptions {
	
	// NOTE: This class is equivalent to TransformConfigOptions, but for import (external data) instead of transform (already committed).

	/*
	 * The itemId is a separate parameter.
	 * 
	 * Folder: 
	 *  - The itemId is the folder where the item should be imported. 
	 *  - The folder must be configured with auto-naming when importing non-XML.
	 *  - The folder can be configured with auto-naming when importing XML (with or without XSL transform).
	 *  - A folder with auto-naming will only allow a primary output from XSL transform.
	 *  - A folder without auto-naming will not allow a primary output from XSL transform.
	 *  
	 *  File:
	 *   - The imported file will become that itemId (param 'overwrite' must be true if item already exists).
	 *   - TBD: Will additional output be allowed from XSL transform?
	 */	
	
	/*
	 * Params:
	 * - 'comment': History comment for the commit.
	 * - 'overwrite': Must be true in order to allow overwriting an existing item in CMS.
	 * 
	 * - Future: 'TransformNN' 
	 */
	private Map <String, String> params = new HashMap<>();
	private String url; // Typically an http / https url, no authentication required. Redirects must be followed.
	private String content; // Content to import, typically XML or JSON.
	private Map <String, String> properties = new HashMap<>(); // Properties to set on the item.
	
	
	public Map<String, String> getParams() {
		return params;
	}
	public void setParams(Map<String, String> params) {
		this.params = params;
	}
	public String getUrl() {
		return url;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public Map<String, String> getProperties() {
		return properties;
	}
	public void setProperties(Map<String, String> properties) {
		this.properties = properties;
	}
}
