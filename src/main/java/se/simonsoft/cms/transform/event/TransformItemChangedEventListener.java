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
package se.simonsoft.cms.transform.event;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.events.ItemChangedEventListener;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.service.TransformService;

public class TransformItemChangedEventListener implements ItemChangedEventListener {

	private final TransformConfiguration transformConfiguration;
	private final Map<CmsRepository, TransformService> transformServices;
	private final Map<CmsRepository, CmsItemLookup> itemLookup;

	private static final String TRANSFORM_PATHS_WHITE_LIST = "cmsconfig:TransformPaths";
	private static final String TRANSFORM_NAME_PROP_KEY = "abx:TransformName";

	private static final Logger logger = LoggerFactory.getLogger(TransformItemChangedEventListener.class);

	@Inject
	public TransformItemChangedEventListener(
			TransformConfiguration transformConfiguration,
			Map<CmsRepository, TransformService> transformServices,
			Map<CmsRepository, CmsItemLookup> itemLookup
			) {

		this.transformConfiguration = transformConfiguration;
		this.transformServices = transformServices;
		this.itemLookup = itemLookup;
	}

	@Override
	public void onItemChange(CmsItem item) {
		logger.debug("Item change event with id: {}", item.getId());
		if (item.getId().getPegRev() == null) {
			logger.error("Item requires a revision to be transformed: {}", item.getId().getLogicalId());
			throw new IllegalArgumentException("Item requires a revision to be transformed.");
		}
		
		if (item.getKind() != CmsItemKind.File) {
			return;
		}
		
		if (!isWithinTransformPath(item)) {
			logger.info("Transform not enabled for path: {}", item.getId().getRelPath());
			return;
		}

		CmsRepository repo = item.getId().getRepository();
		final TransformService transformService = transformServices.get(repo);
		final Map<String, TransformConfig> configurations = transformConfiguration.getConfiguration(item.getId());
		

		if (!configurations.isEmpty() && item.getProperties().getString(TRANSFORM_NAME_PROP_KEY) != null) {
			logger.debug("Item is the result of a transform, suppressing: {}", item.getId());
			return;
		}

		for (Entry<String, TransformConfig> config: configurations.entrySet()) {
			if (config.getValue().isActive()) {
				
				// TODO: Trigger work execution instead of executing here.
				logger.debug("Config: '{}' is active, transforming...", config.getKey());
				config.getValue().setName(config.getKey());
				try {
					transformService.transform(item, config.getValue());
					logger.debug("Transformed with config: '{}'", config.getKey());
				} catch (Exception e) {
					logger.error("Failed transform '{}': {}", config.getKey(), e.getMessage(), e);
					// Best effort, for now.
					// Could consider storing the exception and throwing outside the loop in order to trigger a retry.
				}
			}
		}
	}
	
	private boolean isWithinTransformPath(CmsItem item) {
		
		CmsRepository repository = item.getId().getRepository();
		CmsItem repoItem = itemLookup.get(repository).getItem(repository.getItemId());
		
		CmsItemProperties properties = repoItem.getProperties();
		String pathsString = properties.getString(TRANSFORM_PATHS_WHITE_LIST);
		
		List<String> whiteListedPaths = new ArrayList<>();
		if (pathsString != null && !pathsString.trim().isEmpty()) {
			String[] split = pathsString.trim().split("\\r?\\n"); //removing surrounding white space and the split on new lines.
			whiteListedPaths.addAll(Arrays.asList(split));
			logger.debug("Number of white listed paths: {}", whiteListedPaths.size());
		}
		
		boolean withinWhiteList = false;
		for (String p: whiteListedPaths) {
			final String trimmedPath = p.trim();
			CmsItemPath whiteListedPath = new CmsItemPath(trimmedPath);
			if (whiteListedPath.isAncestorOf(item.getId().getRelPath())) {
				withinWhiteList = true;
				logger.debug("Item is within a white listed path, will proceed with the transform");
			}
		}
		
		return withinWhiteList;
	}

}
