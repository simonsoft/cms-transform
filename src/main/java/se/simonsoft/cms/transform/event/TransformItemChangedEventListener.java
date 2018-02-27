package se.simonsoft.cms.transform.event;

import javax.inject.Inject;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.events.ItemChangedEventListener;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.xmlsource.transform.TransformerService;

public class TransformItemChangedEventListener implements ItemChangedEventListener {
	
	private final TransformConfiguration transformConfiguration;
	private final TransformerService transformerService;

	@Inject
	public TransformItemChangedEventListener(
			TransformConfiguration transformConfiguration,
			TransformerService transformerService
			) {
		
		this.transformConfiguration = transformConfiguration;
		this.transformerService = transformerService;
	
	}

	@Override
	public void onItemChange(CmsItem item) {
		
	}

}
