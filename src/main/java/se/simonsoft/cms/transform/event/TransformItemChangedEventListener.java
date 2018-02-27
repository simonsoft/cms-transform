package se.simonsoft.cms.transform.event;

import javax.inject.Inject;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.events.ItemChangedEventListener;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.xmlsource.transform.TransformerService;

public class TransformItemChangedEventListener implements ItemChangedEventListener {
	
	private final TransformConfiguration transformConfiguration;
	private final TransformerService transformerService;
	private final CmsCommit commit;
	private final CmsItemLookup lookup;

	@Inject
	public TransformItemChangedEventListener(
			TransformConfiguration transformConfiguration,
			TransformerService transformerService,
			CmsCommit commit,
			CmsItemLookup lookup
			) {
		
		this.transformConfiguration = transformConfiguration;
		this.transformerService = transformerService;
		this.commit = commit;
		this.lookup = lookup;
	
	}

	@Override
	public void onItemChange(CmsItem item) {
		
	}

}
