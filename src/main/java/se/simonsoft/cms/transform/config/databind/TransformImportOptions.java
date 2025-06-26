package se.simonsoft.cms.transform.config.databind;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true) // Allow future changes.
public class TransformImportOptions {

	// private String type; // Not yet used, could be "xsl" in the future.
	/*
	 * Params:
	 * - 'comment': History comment for the commit.
	 * - 'overwrite': Must be true in order to allow overwriting an existing item in CMS.
	 * 
	 * - Future: 'TransformNN' 
	 */
	private Map <String, String> params = new HashMap<>();
	private String url; // Typically an http / https url, no authentication required.
	private Map <String, String> properties = new HashMap<>(); // Properties to set on the item.
	
	
}
