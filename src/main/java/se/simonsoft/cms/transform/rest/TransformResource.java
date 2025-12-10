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
import javax.inject.Named;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Path("/transform5")
public class TransformResource {

    private final Logger logger = LoggerFactory.getLogger(TransformResource.class);
    private final Map<CmsRepository, TransformService> transformServiceMap;
    private final ObjectWriter objectWriter;
    private String hostname;

    private static final int MAX_CONTENT_SIZE_MB = 5;

    @Inject
    public TransformResource(
            @Named("config:se.simonsoft.cms.hostname") String hostname,
            Map<CmsRepository, TransformService> transformServiceMap,
            ObjectWriter objectWriter) {
        this.hostname = hostname;
        this.transformServiceMap = transformServiceMap;
        this.objectWriter = objectWriter;
    }

    @POST
    @Path("api/import")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response importItem(@QueryParam("item") CmsItemIdArg item, String body) throws JsonProcessingException {

        if (item == null) {
            throw new IllegalArgumentException("Field 'item': required");
        }

        item.setHostnameOrValidate(hostname);

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

        String url = importOptions.getUrl();
        String content = importOptions.getContent();

        if ((content != null && url != null) || (content == null && url == null)) {
            throw new IllegalArgumentException("Import requires either a valid URL or content.");
        } else if (content != null && content.length() > MAX_CONTENT_SIZE_MB * 1024 * 1024) {
            throw new IllegalArgumentException(String.format("Largest allowed content size is %d MBs.", MAX_CONTENT_SIZE_MB));
        }

        try {
            Map<String, Set<CmsItemId>> response = new HashMap<>();
            Set<CmsItemId> items = transformServiceMap.get(item.getRepository()).importItem(item, importOptions);
            response.put("items", items);
            return Response.ok().entity(objectWriter.writeValueAsString(response)).build();
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid input parameters for item: {}, importOptions: {}", item, body, e);
            throw e;
        } catch (Exception e) {
            logger.error("Import failed for item: {}, error: {}", item, e.getMessage());
            throw e;
        }
    }
}
