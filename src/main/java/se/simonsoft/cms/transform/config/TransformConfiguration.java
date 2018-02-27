package se.simonsoft.cms.transform.config;

import java.util.Map;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

public interface TransformConfiguration {
	
	public Map<String, TransformConfig> getConfiguration(CmsItemId itemId);

}
