/**
 * Copyright (C) 2009-2017 Simonsoft Nordic AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
			throw new IllegalArgumentException("Getting configurations requires a valid CmsItemId");
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
					logger.error("Could not deserialize config: {} to new TransformConfiguration", name.concat(":" + o.getKey()));
					throw new RuntimeException(e);
				}
			}
		}
		
		return configs;
	}
}
