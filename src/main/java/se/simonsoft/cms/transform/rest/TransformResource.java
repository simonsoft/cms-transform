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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;

import java.util.Map;

@Path("/transform5")
public class TransformResource {

    private final Logger logger = LoggerFactory.getLogger(TransformResource.class);
    private final Map<CmsRepository, TransformService> transformServiceMap;

    @Inject
    public TransformResource(Map<CmsRepository, TransformService> transformServiceMap) {
        this.transformServiceMap = transformServiceMap;
    }

    @POST
    @Path("api/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importItem(@QueryParam("item") CmsItemIdArg itemId, String body) {

        if (itemId == null) {
            throw new IllegalArgumentException("Field 'item': required");
        }

        TransformImportOptions importOptions;

        try {
            ObjectMapper mapper = new ObjectMapper();
            importOptions = mapper.readValue(body, TransformImportOptions.class);
        } catch (JsonProcessingException e) {
            logger.error("API request with invalid JSON body: {}", body, e);
            throw new IllegalArgumentException("Failed to parse request body: " + e.getMessage(), e);
        }

        if (importOptions == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\": \"Import options must be provided in request body\"}")
                    .build();
        }

        try {
            transformServiceMap.get(itemId.getRepository()).importItem(itemId, importOptions);

            String successMessage = "Import completed successfully for item: " + itemId.getLogicalId();
            return Response.ok()
                    .entity("{\"message\": \"" + successMessage + "\"}")
                    .build();
                    
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid itemId parameter: {}", itemId.getLogicalId(), e);
            throw e;
        } catch (Exception e) {
            logger.error("Import failed for itemId: {}", itemId.getLogicalId(), e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\": \"Import failed: " + e.getMessage() + "\"}")
                    .build();
        }
    }
}
