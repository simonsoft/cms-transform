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
package se.simonsoft.cms.transform.command;

import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.command.CommandRuntimeException;
import se.simonsoft.cms.item.command.ExternalCommandHandler;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.service.TransformService;

public class TransformCommandHandler implements ExternalCommandHandler<TransformConfig> {

	private final Logger logger = LoggerFactory.getLogger(TransformCommandHandler.class);
	
	private final Map<CmsRepository, TransformService> transformServiceMap;
	
	@Inject
	public TransformCommandHandler(Map<CmsRepository, TransformService> transformServiceMap) {
		this.transformServiceMap = transformServiceMap;
	}
	
	@Override
	public Class<TransformConfig> getArgumentsClass() {
		return TransformConfig.class;
	}
	
	@Override
	public String handleExternalCommand(CmsItemId item, TransformConfig arguments) {
		
		if (arguments == null || arguments.getOptions() == null) {
			throw new CommandRuntimeException("BadRequest", "TransformConfig / TransformConfigOptions must not be null.");
		}
		if (!"xsl".equals(arguments.getOptions().getType())) {
			throw new CommandRuntimeException("BadRequest", "Transform is not a supported type: " + arguments.getOptions().getType());
		}
		
		TransformService transformService = this.transformServiceMap.get(item.getRepository());
		try {
			transformService.transform(item, arguments);
			logger.debug("Transformed with config: '{}'", arguments);
		} catch (Exception e) {
			// TODO: Catch more specific exceptions in order to control whether retry is suitable.
			logger.error("Transform failed '{}': {}", arguments, e.getMessage(), e);
			throw new CommandRuntimeException("TransformFailed", e.getMessage());
		}
		return null; // Must return JSON. Consider defining a return type instead of String.
	}

}
