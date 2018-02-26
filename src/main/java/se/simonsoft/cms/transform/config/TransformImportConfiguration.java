package se.simonsoft.cms.transform.config;

import java.util.Map;

import se.simonsoft.cms.item.CmsItemId;

public interface TransformImportConfiguration {
	
	public Map<String, TransformImportConfiguration> getConfiguration(CmsItemId itemId);

}
