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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import se.simonsoft.cms.transform.testconfig.SvnDatasetTest;

import jakarta.inject.Inject;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationException;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationIssue;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationError;

@QuarkusTest
public class TransformServiceXslSchemaTest extends SvnDatasetTest {

	@Inject TransformService transformService;

	private static final String transformTestDoc = "x-svn:///svn/repo1/doc/transform-test.xml";
	private static final String transformTestMap = "x-svn:///svn/repo1/doc/transform-test-map.ditamap";
	private static final String transformTestDocStrict = "x-svn:///svn/repo1/strict/transform-test.xml";


	private TransformConfig getConfig(Map<String, String> params) {
		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-schema.xsl");
		optionsParams.put("overwrite", "true"); // Principal output replaces the base item, which is locked.
		optionsParams.put("comment", "Transform with schema validation");
		optionsParams.putAll(params);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");
		configOptions.setParams(optionsParams);

		TransformConfig config = new TransformConfig();
		config.setActive(true);
		config.setName("schema");
		config.setOptions(configOptions);
		return config;
	}

	private String getContents(String itemId) throws Exception {
		CmsItem item = lookup.getItem(new CmsItemIdArg(itemId));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		item.getContents(baos);
		return baos.toString(StandardCharsets.UTF_8.name());
	}

	/**
	 * Asserts that nothing was committed and that the lock on the base item was released.
	 */
	private void assertNotCommittedAndUnlocked(long youngest) throws Exception {
		assertEquals(youngest, repoLookup.getYoungest(repo).getNumber(), "nothing committed");
		try {
			lookup.getItem(new CmsItemIdArg(transformTestMap));
			fail("map should not be committed");
		} catch (CmsItemNotFoundException e) {
			// expected
		}
		// Locking the base item again fails unless the lock was released.
		transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(new HashMap<>()));
		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
	}

	@Test
	public void testSchemaVariantFromExtension() {
		assertEquals("ditamap", TransformSchemaValidation.getSchemaVariant(new CmsItemPath("/doc/map.ditamap")));
		assertEquals("ditabase", TransformSchemaValidation.getSchemaVariant(new CmsItemPath("/doc/topic.dita")));
		assertEquals("ditabase", TransformSchemaValidation.getSchemaVariant(new CmsItemPath("/doc/topic.xml")));
		assertEquals("ditabase", TransformSchemaValidation.getSchemaVariant(new CmsItemPath("/doc/keydefmap.xml")));
	}

	@Test
	public void testValidCommits() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");

		// Topic and map are validated with the built-in ditabase and ditamap schemas respectively.
		transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));

		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
		assertTrue(getContents(transformTestMap).contains("<techdocmap>"));
		assertTrue(getContents(transformTestDoc).contains("<subtitle>"));
	}

	@Test
	public void testInvalidSeveralItemsAbortsCommit() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("invalid", "true"); // XSL param

		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail schema validation");
		} catch (TransformSchemaValidationException e) {
			// 3 x para in the topic, reltable in the map.
			assertEquals("Schema validation failed: 2 of 2 items, 4 issues", e.getMessage());
			assertEquals(2, e.getItemFailedCount());
			assertEquals(4, e.getIssues().size());
			// Issues of all items, with the path that would have been committed.
			TransformSchemaValidationIssue first = e.getIssues().get(0);
			assertEquals("/doc/transform-test.xml", first.getPath().getPath());
			assertEquals(SchemaValidationError.Level.ERROR, first.getError().getLevel());
			assertEquals(Integer.valueOf(33), first.getError().getLineNumber());
			assertEquals(Integer.valueOf(16), first.getError().getColumnNumber());
			assertEquals("/document[1]/body[1]/section[1]/para[1]", first.getError().getElementPath());
			assertTrue(first.getError().getMessage().startsWith("element \"para\" not allowed anywhere"), first.getError().getMessage());
			assertEquals("/doc/transform-test-map.ditamap", e.getIssues().get(3).getPath().getPath());
		}
		assertNotCommittedAndUnlocked(youngest);
	}

	@Test
	public void testValidDoctypeNotLoaded() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("doctype", "true"); // XSL param: map with DOCTYPE, the DTD does not exist.

		// Validation must not try to load the DTD (cms-xmlsource 0.22.2).
		transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));

		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
		String map = getContents(transformTestMap);
		assertTrue(map.contains("<!DOCTYPE techdocmap"), map);
		assertTrue(map.contains("missing.dtd"), map);
	}

	@Test
	public void testInvalidNotValidatedWithoutSchemaValidate() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("invalid", "true"); // XSL param

		transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
		assertTrue(getContents(transformTestDoc).contains("<para>"));
	}

	@Test
	public void testSchemaQname() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("SchemaQualifiedName", "strict"); // The strict schema application does not allow subtitle.

		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail schema validation");
		} catch (TransformSchemaValidationException e) {
			assertEquals("Schema validation failed: 1 of 2 items, 1 issues", e.getMessage());
		}
		assertNotCommittedAndUnlocked(youngest);
	}

	@Test
	public void testSchemaQnameUnknown() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("SchemaQualifiedName", "nonexisting");

		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail, unknown schema application");
		} catch (IllegalArgumentException e) {
			assertEquals("Schema file is missing: nonexisting/ditabase.s.rng", e.getMessage());
		}
		assertNotCommittedAndUnlocked(youngest);
	}

	@Test
	public void testSchemaQualifiedNameConfigured() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");

		// Project /strict is configured with cmsconfig:SchemaQualifiedName = strict.
		try {
			transformService.transform(new CmsItemIdArg(transformTestDocStrict), getConfig(params));
			fail("Should fail schema validation");
		} catch (TransformSchemaValidationException e) {
			assertEquals("Schema validation failed: 1 of 2 items, 1 issues", e.getMessage());
		}
		assertEquals(youngest, repoLookup.getYoungest(repo).getNumber(), "nothing committed");
	}

	@Test
	public void testSchemaQualifiedNameParamOverridesConfigured() throws Exception {
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("SchemaQualifiedName", "nonexisting");

		try {
			transformService.transform(new CmsItemIdArg(transformTestDocStrict), getConfig(params));
			fail("Should fail, unknown schema application");
		} catch (IllegalArgumentException e) {
			assertEquals("Schema file is missing: nonexisting/ditabase.s.rng", e.getMessage());
		}
	}

	@Test
	public void testSchemaVariantOverride() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("SchemaVariant", "ditabase"); // The map is invalid as ditabase.

		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail schema validation");
		} catch (TransformSchemaValidationException e) {
			assertTrue(e.getMessage().startsWith("Schema validation failed: 1 of 2 items, "), e.getMessage());
		}
		assertNotCommittedAndUnlocked(youngest);
	}

	@Test
	public void testSchemaVariantUnsupported() throws Exception {
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");
		params.put("SchemaVariant", "ditatopic");

		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail, unsupported variant");
		} catch (IllegalArgumentException e) {
			assertEquals("Unsupported SchemaVariant: ditatopic", e.getMessage());
		}
	}

	@Test
	public void testSchemaServiceNotConfigured() throws Exception {
		TransformService transformServiceNoSchema = new TransformServiceXsl(commit, lookup, lookupReporting, repoLookup,
				transformerServiceFactory, sourceReader, nameFactory, null);
		Map<String, String> params = new HashMap<>();
		params.put("SchemaValidate", "true");

		try {
			transformServiceNoSchema.transform(new CmsItemIdArg(transformTestDoc), getConfig(params));
			fail("Should fail, no schema service");
		} catch (IllegalStateException e) {
			assertEquals("Schema validation is requested (SchemaValidate) but no schema service is configured.", e.getMessage());
		}
	}

	@Test
	public void testImportItem() throws Exception {
		long youngest = repoLookup.getYoungest(repo).getNumber();
		CmsItemId folder = new CmsItemIdArg("x-svn:///svn/repo1/doc");
		Map<String, String> params = new HashMap<>();
		params.put("pathnamebase", "imported");
		params.put("pathext", "ditamap");
		params.put("SchemaValidate", "true");
		TransformImportOptions options = new TransformImportOptions();
		options.setParams(params);

		options.setContent("<techdocmap><reltable/></techdocmap>");
		try {
			transformService.importItem(folder, options);
			fail("Should fail schema validation");
		} catch (TransformSchemaValidationException e) {
			assertEquals("Schema validation failed: 1 of 1 items, 1 issues", e.getMessage());
		}
		assertEquals(youngest, repoLookup.getYoungest(repo).getNumber(), "nothing committed");

		options.setContent("<techdocmap><title>Imported</title></techdocmap>");
		Set<CmsItemId> result = transformService.importItem(folder, options);
		assertEquals(1, result.size());
		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
	}
}
