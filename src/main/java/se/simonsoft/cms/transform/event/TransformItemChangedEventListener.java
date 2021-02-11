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
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.events.ItemChangedEventListener;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.item.workflow.WorkflowExecutionException;
import se.simonsoft.cms.item.workflow.WorkflowExecutor;
import se.simonsoft.cms.item.workflow.WorkflowItemInput;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

public class TransformItemChangedEventListener implements ItemChangedEventListener {

	private final TransformConfiguration transformConfiguration;
	private final Map<CmsRepository, CmsItemLookup> itemLookup;
	// Optionally override with injected userId, 
	// e.g. when normal users upload files but should not be allowed to modify transform result.
	private final String userId; 
	private final WorkflowExecutor<WorkflowItemInput> workflowExecutor;

	private static final String TRANSFORM_PATHS_WHITE_LIST = "cmsconfig:TransformPaths";
	private static final String TRANSFORM_NAME_PROP_KEY = "abx:TransformName";

	private static final Logger logger = LoggerFactory.getLogger(TransformItemChangedEventListener.class);

	@Inject
	public TransformItemChangedEventListener(
			TransformConfiguration transformConfiguration,
			Map<CmsRepository, CmsItemLookup> itemLookup,
			@Named("config:se.simonsoft.cms.transform.userid") String userId,
			@Named("config:se.simonsoft.cms.aws.work.workflow") WorkflowExecutor<WorkflowItemInput> workflowExecutor
			) {

		this.transformConfiguration = transformConfiguration;
		this.itemLookup = itemLookup;
		this.userId = userId;
		this.workflowExecutor = workflowExecutor;
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

		final Map<String, TransformConfig> configurations = transformConfiguration.getConfiguration(item.getId());

		if (!configurations.isEmpty() && item.getProperties().getString(TRANSFORM_NAME_PROP_KEY) != null) {
			logger.debug("Item is the result of a transform, suppressing: {}", item.getId());
			return;
		}

		for (Entry<String, TransformConfig> e: configurations.entrySet()) {
			if (e.getValue().isActive()) {
				TransformConfig config = e.getValue();
				// Trigger work execution instead of executing here.
				// Supports injecting a userId to override the work command user, see doTransformWorkEnqueue;
				logger.debug("Config: '{}' is active, starting work execution.", e.getKey());
				config.setName(e.getKey());
				doTransformWorkEnqueue(item, config);
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
			}
		}
		
		return withinWhiteList;
	}
	
	private void doTransformWorkEnqueue(CmsItem item, TransformConfig config) {
		TransformItemWorkflowInput job = new TransformItemWorkflowInput(item.getId(), config);
		// Set the injected userId override, if configured.
		// Otherwise, the WorkflowExecutor will set current user.
		if (this.userId != null && !this.userId.trim().isEmpty()) {
			job.setUserId(userId);
		}
		try {
			workflowExecutor.startExecution(job);
		} catch (WorkflowExecutionException e) {
			logger.error("Failed to start execution for itemId '{}': {}", job.getItemId(), e.getMessage());
			throw new RuntimeException("Work execution failed: " + e.getMessage(), e);
		}
	}

}
