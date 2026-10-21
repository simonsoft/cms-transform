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
package se.simonsoft.cms.transform.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.command.CommandRuntimeException;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.commit.FilePropertyChange;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsAuthenticationException;
import se.simonsoft.cms.item.properties.CmsItemPropertiesMap;
import se.simonsoft.cms.transform.command.TransformCommandHandler;
import se.simonsoft.cms.transform.command.TransformImportCommandHandler;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.testconfig.SvnDatasetTest;
import se.simonsoft.cms.transform.testconfig.TestTransformContext;

@QuarkusTest
class TransformCdiTest extends SvnDatasetTest {

    @Inject TransformService service;
    @Inject TransformConfiguration configuration;
    @Inject TransformCommandHandler transform;
    @Inject TransformImportCommandHandler importer;
    @Inject TestTransformContext selection;
    @ConfigProperty(name = "quarkus.svn.username") String username;
    @ConfigProperty(name = "quarkus.svn.password") String password;

    @Test
    void commandValidationAndResultsAreFreshAfterFailureAndSuccess() throws Exception {
        CmsItemId item = item("/doc/transform-test.xml");
        long before = repoLookup.getYoungest(repo).getNumber();
        for (int operation = 0; operation < 2; operation++) {
            CommandRuntimeException failure = assertThrows(CommandRuntimeException.class,
                    () -> transform.handleExternalCommand(item, config(true)));
            assertEquals("SchemaValidationFailed", failure.getErrorName());
            var error = new ObjectMapper().readTree(failure.getMessage());
            assertEquals("Schema validation failed: 2 of 2 items, 4 issues", error.get("message").asText());
            assertEquals(4, error.get("issues").size());
            assertEquals(before + operation, repoLookup.getYoungest(repo).getNumber());
            assertNull(lookup.getLocked(item));
            if (operation == 0) {
                assertThrows(CmsItemNotFoundException.class, () -> lookup.getItem(item("/doc/transform-test-map.ditamap")));
            }
            assertNull(transform.handleExternalCommand(item, config(false)));
            assertEquals(before + operation + 1, repoLookup.getYoungest(repo).getNumber());
            assertFalse(contents(item).contains("<para>"));
            assertFalse(contents(item("/doc/transform-test-map.ditamap")).contains("<reltable"));
            assertNull(lookup.getLocked(item));
        }
    }

    @Test
    void servicesAndRepositoryDataAreIsolatedBetweenJobs() throws Throwable {
        String[] ids = {"test-" + UUID.randomUUID(), "test-" + UUID.randomUUID()};
        List<List<Object>> instances = new ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            final int job = i;
            inContext(ids[i], "repo" + (i + 1), username, password, () -> {
                assertEquals("repo" + (job + 1), repo.getName());
                assertEquals(1, repoLookup.getYoungest(repo).getNumber());
                var firstConfig = configuration.getConfiguration(item("/doc/transform-test.xml"));
                assertTrue(firstConfig.get("single").isActive());
                firstConfig.get("single").setActive(false);
                assertTrue(configuration.getConfiguration(item("/doc/transform-test.xml")).get("single").isActive());
                instances.add(List.of(ClientProxy.unwrap(service), ClientProxy.unwrap(configuration),
                        ClientProxy.unwrap(transform), ClientProxy.unwrap(importer), ClientProxy.unwrap(svn.get())));
                assertSame(instances.get(job).get(0), ClientProxy.unwrap(service));
                assertEquals("Import completed successfully for item: " + item("/doc").getLogicalId(),
                        importer.handleExternalCommand(item("/doc"), options("job", "Job " + job)));
                assertEquals(2, repoLookup.getYoungest(repo).getNumber());
                assertEquals(username, revisionProperty(repoLookup.getYoungest(repo), "svn:author"));
            });
        }
        for (int bean = 0; bean < instances.get(0).size(); bean++) {
            assertNotSame(instances.get(0).get(bean), instances.get(1).get(bean));
        }
        for (int i = 0; i < ids.length; i++) {
            final int job = i;
            inContext(ids[i], "repo" + (i + 1), username, password, () -> {
                assertEquals(2, repoLookup.getYoungest(repo).getNumber());
                assertTrue(contents(item("/doc/job.ditamap")).contains("Job " + job));
                assertFalse(contents(item("/doc/job.ditamap")).contains("Job " + (1 - job)));
            });
        }
    }

    @Test
    void rejectedCredentialsDoNotReuseThePreviousJobsSession() throws Throwable {
        String id = "test-" + UUID.randomUUID();
        inContext(id, "repo1", username, password, () -> {
            transform.handleExternalCommand(item("/doc/transform-test.xml"), config(false));
            assertEquals(2, repoLookup.getYoungest(repo).getNumber());
        });
        inContext(id, "repo1", "unauthorized-user", "invalid-password", () -> {
            assertThrows(CmsAuthenticationException.class,
                    () -> service.transform(item("/doc/transform-test.xml"), config(false)));
        });
        inContext(id, "repo1", username, password, () -> {
            assertEquals(2, repoLookup.getYoungest(repo).getNumber());
            assertNull(lookup.getLocked(item("/doc/transform-test.xml")));
            transform.handleExternalCommand(item("/doc/transform-test.xml"), config(false));
            assertEquals(3, repoLookup.getYoungest(repo).getNumber());
            assertEquals(username, revisionProperty(repoLookup.getYoungest(repo), "svn:author"));
        });
    }

    @Test
    void importCommandValidatesBeforeCommitAndCanRetryWithCorrectedContent() throws Exception {
        var options = options("imported", "Valid");
        options.setContent("<techdocmap><reltable/></techdocmap>");
        long before = repoLookup.getYoungest(repo).getNumber();
        CommandRuntimeException failure = assertThrows(CommandRuntimeException.class,
                () -> importer.handleExternalCommand(item("/doc"), options));
        assertEquals("SchemaValidationFailed", failure.getErrorName());
        assertEquals(1, new ObjectMapper().readTree(failure.getMessage()).get("issues").size());
        assertEquals(before, repoLookup.getYoungest(repo).getNumber());
        assertThrows(CmsItemNotFoundException.class, () -> lookup.getItem(item("/doc/imported.ditamap")));
        options.setContent("<techdocmap><title>Corrected</title></techdocmap>");
        importer.handleExternalCommand(item("/doc"), options);
        assertEquals(before + 1, repoLookup.getYoungest(repo).getNumber());
        assertTrue(contents(item("/doc/imported.ditamap")).contains("Corrected"));
        assertNull(lookup.getLocked(item("/doc/imported.ditamap")));
    }

    @Test
    void importFromLocalUrlKeepsResultsSeparateFromTheNextImport() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/source", exchange -> {
            byte[] xml = "<techdocmap><title>From URL</title></techdocmap>".getBytes(StandardCharsets.UTF_8);
            try (exchange) {
                exchange.sendResponseHeaders(200, xml.length);
                exchange.getResponseBody().write(xml);
            }
        });
        server.start();
        try {
            var url = options("from-url", "unused");
            url.setContent(null);
            url.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/source");
            var first = service.importItem(item("/doc"), url);
            var second = service.importItem(item("/doc"), options("from-content", "From content"));
            assertEquals(1, first.size());
            assertEquals(1, second.size());
            assertEquals(new CmsItemPath("/doc/from-url.ditamap"), first.iterator().next().getRelPath());
            assertEquals(new CmsItemPath("/doc/from-content.ditamap"), second.iterator().next().getRelPath());
            assertTrue(contents(first.iterator().next()).contains("From URL"));
            assertTrue(contents(second.iterator().next()).contains("From content"));
            assertEquals(3, repoLookup.getYoungest(repo).getNumber());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void repeatedImportsUseTheRealNamingFactoryAndCreateTheNewShard() throws Exception {
        CmsPatchset properties = new CmsPatchset(repo, repoLookup.getYoungest(repo));
        properties.add(new FilePropertyChange(new CmsItemPath("/doc"), new CmsItemPropertiesMap("cms:class", "shardparent")
                .and("cmsconfig:ItemNamePattern", "NOTE_00#####")));
        commit.run(properties);
        var options = options("unused", "Named");
        options.getParams().remove("pathnamebase");
        var first = service.importItem(item("/doc"), options).iterator().next();
        var second = service.importItem(item("/doc"), options).iterator().next();
        assertNotEquals(first.getRelPath(), second.getRelPath());
        assertEquals(first.getRelPath().getParent(), second.getRelPath().getParent());
        assertTrue(contents(first).contains("Named"));
        assertTrue(contents(second).contains("Named"));
        assertEquals(4, repoLookup.getYoungest(repo).getNumber());
    }

    private CmsItemId item(String path) {
        return repo.getItemId().withRelPath(new CmsItemPath(path));
    }

    private String contents(CmsItemId item) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        lookup.getItem(item).getContents(output);
        return output.toString(StandardCharsets.UTF_8);
    }

    private TransformConfig config(boolean invalid) {
        var options = new TransformConfigOptions();
        options.setType("xsl");
        options.setParams(Map.of("stylesheet", "/stylesheet/transform-schema.xsl", "overwrite", "true",
                "SchemaValidate", "true", "invalid", Boolean.toString(invalid), "comment", "CDI transform"));
        var config = new TransformConfig();
        config.setActive(true);
        config.setOptions(options);
        return config;
    }

    private TransformImportOptions options(String name, String title) {
        var options = new TransformImportOptions();
        options.setParams(new HashMap<>(Map.of("pathnamebase", name, "pathext", "ditamap", "SchemaValidate", "true")));
        options.setContent("<techdocmap><title>" + title + "</title></techdocmap>");
        return options;
    }

    private void inContext(String repositoryId, String repositoryName, String user, String secret,
            Executable job) throws Throwable {
        var context = Arc.container().requestContext();
        var outer = context.isActive() ? context.getState() : null;
        if (outer != null) context.deactivate();
        context.activate();
        try {
            selection.select(repositoryId, repositoryName, user, secret);
            job.execute();
        } finally {
            context.terminate();
            if (outer != null) context.activate(outer);
        }
    }
}
