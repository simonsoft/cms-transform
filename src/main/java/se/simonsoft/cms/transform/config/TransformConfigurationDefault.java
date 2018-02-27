package se.simonsoft.cms.transform.config;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectReader;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.config.CmsConfigOption;
import se.simonsoft.cms.item.config.CmsResourceContext;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

public class TransformConfigurationDefault implements TransformConfiguration {
	
	
	private static final Logger logger = LoggerFactory.getLogger(TransformConfigurationDefault.class);
	private final CmsRepositoryLookup repositoryLookup;
	private final ObjectReader reader;
	
	private static final String TRANSFORM_IMPORT_NAMESPACE = "cmsconfig-transform";

	@Inject
	public TransformConfigurationDefault(
			CmsRepositoryLookup repositoryLookup,
			ObjectReader reader
			) {
		
		this.repositoryLookup = repositoryLookup;
		this.reader = reader.forType(TransformConfig.class);
	}

	@Override
	public Map<String, TransformConfig> getConfiguration(CmsItemId itemId) {
		
		if (itemId == null) {
			throw new IllegalArgumentException("Getting configurations requiers a valid CmsItemId");
		}
		
		CmsResourceContext context = getConfigurationParentFolder(itemId);
		return deserializeConfig(context);
	}

	private CmsResourceContext getConfigurationParentFolder(CmsItemId itemId) {

		// Getting config for the parent, which is always a Folder.
		CmsItemPath relPath = itemId.getRelPath().getParent();
		logger.debug("Configuration context: {} - ({})", relPath, itemId);
		CmsItemId folder = itemId.getRepository().getItemId(relPath, null); //Always getting config from HEAD.
		CmsResourceContext context = repositoryLookup.getConfig(folder, CmsItemKind.Folder);
		return context;
	}

	private Map<String, TransformConfig> deserializeConfig(CmsResourceContext context) {
		logger.debug("Starting deserialization of configs with namespace {}...", TRANSFORM_IMPORT_NAMESPACE);
		
		Map<String, TransformConfig> configs = new LinkedHashMap<>();
		
		for (CmsConfigOption o: context) {
			final String name = o.getNamespace();
			if (name.startsWith(TRANSFORM_IMPORT_NAMESPACE)) {
				try {
					TransformConfig config = reader.readValue(o.getValueString());
					configs.put(o.getKey(), config);
				} catch (IOException e) {
					logger.error("Could not deserialize config: {} to new TransformImportConfiguration", name.concat(":" + o.getKey()));
					throw new RuntimeException(e);
				}
			}
		}
		
		return configs;
	}
}
