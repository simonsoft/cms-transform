package se.simonsoft.cms.transform.service;

import javax.inject.Inject;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceDocumentS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerService;

public class TransformServiceXsl implements TransformService {
	
	private final CmsCommit commit;
	private final CmsItemLookup lookup;
	private final TransformerService transformerService;

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			TransformerService transfromerService
			) {
		
		this.commit = commit;
		this.lookup = itemLookup;
		this.transformerService = transfromerService;
	}

	@Override
	public void transform(CmsItem item, TransformConfig config) {
		
		
	}

}
