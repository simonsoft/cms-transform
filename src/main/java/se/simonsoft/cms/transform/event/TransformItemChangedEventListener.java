package se.simonsoft.cms.transform.event;

import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.events.ItemChangedEventListener;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.service.TransformService;

public class TransformItemChangedEventListener implements ItemChangedEventListener {
	
	private final TransformConfiguration transformConfiguration;
	private final Map<CmsRepository, TransformService> transformServices;
	
	
	private static final Logger logger = LoggerFactory.getLogger(TransformItemChangedEventListener.class);

	@Inject
	public TransformItemChangedEventListener(
			TransformConfiguration transformConfiguration,
			Map<CmsRepository, TransformService> transformServices
			) {
		
		this.transformConfiguration = transformConfiguration;
		this.transformServices = transformServices;
	
	}

	@Override
	public void onItemChange(CmsItem item) {
		
		final TransformService transformService = transformServices.get(item.getId().getRepository());
		final Map<String, TransformConfig> configurations = transformConfiguration.getConfiguration(item.getId());
		
		for (Entry<String, TransformConfig> e: configurations.entrySet()) {
			if (e.getValue().isActive()) {
				logger.debug("Config: '{}' is active, transforming...", e.getKey());
				RepoRevision revision = transformService.transform(item, e.getValue());
				String stylesheet = e.getValue().getOptions().getParams().get("stylesheet");
				logger.debug("ItemId: {}, has been transformed with stylesheet: '{}', rev: {}", item.getId(), stylesheet, revision.getNumber());
			}
		}
		
	}

}
