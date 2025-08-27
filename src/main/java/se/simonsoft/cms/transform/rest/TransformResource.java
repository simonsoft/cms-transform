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
package se.simonsoft.cms.transform.rest;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;

@Path("/transform5")
public class TransformResource {

    private final Logger logger = LoggerFactory.getLogger(TransformResource.class);
    private final TransformService transformService;

    @Inject
    public TransformResource(TransformService transformService) {
        this.transformService = transformService;
    }

    @POST
    @Path("/api/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importItem(@QueryParam("itemId") String itemIdParam, TransformImportOptions importOptions) {

        if (itemIdParam == null || itemIdParam.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"itemId query parameter is required\"}")
                    .build();
        }

        if (importOptions == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Import options must be provided in request body\"}")
                    .build();
        }

        try {
            CmsItemId itemId = new CmsItemIdArg(itemIdParam);
            transformService.importItem(itemId, importOptions);
            
            String successMessage = "Import completed successfully for item: " + itemId.getLogicalId();
            return Response.ok()
                    .entity("{\"message\": \"" + successMessage + "\"}")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid itemId parameter: {}", itemIdParam, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Invalid itemId parameter: " + e.getMessage() + "\"}")
                    .build();
                    
        } catch (Exception e) {
            logger.error("Import failed for itemId: {}", itemIdParam, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Import failed: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
