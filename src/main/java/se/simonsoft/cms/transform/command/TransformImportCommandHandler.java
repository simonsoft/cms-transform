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

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.command.ExternalCommandHandler;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.service.TransformService;

public class TransformImportCommandHandler implements ExternalCommandHandler<TransformImportOptions> {

    private final Logger logger = LoggerFactory.getLogger(TransformImportCommandHandler.class);
    private final TransformService transformService;

    @Inject
    public TransformImportCommandHandler(TransformService transformService) {
        this.transformService = transformService;
    }

    @Override
    public String handleExternalCommand(CmsItemId item, TransformImportOptions arguments) {
        
        logger.info("Starting import for item: {} from URL: {}", item.getLogicalId(), arguments.getUrl());
        
        try {
            transformService.importItem(item, arguments);
            String successMessage = "Import completed successfully for item: " + item.getLogicalId();
            logger.info(successMessage);
            return successMessage;
            
        } catch (Exception e) {
            String errorMessage = "Import failed for item: " + item.getLogicalId() + " - " + e.getMessage();
            logger.error(errorMessage, e);
            throw new RuntimeException(errorMessage, e);
        }
    }

    @Override
    public Class<TransformImportOptions> getArgumentsClass() {
        return TransformImportOptions.class;
    }
}
