package se.simonsoft.cms.transform.service;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

public interface TransformService {
	
	RepoRevision transform(CmsItem item, TransformConfig config);
}