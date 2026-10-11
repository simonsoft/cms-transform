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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import net.sf.saxon.s9api.Processor;
import se.repos.testing.indexing.ReposTestIndexing;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.testconfig.TestFileXmlSetUp;
import se.simonsoft.cms.transform.testconfig.TestTransformSchemaService;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXslSchemaTest {

	private ReposTestIndexing indexing;
	private TestFileXmlSetUp testSetUp;
	private CmsRepository repo;
	private CmsItemLookup lookup;
	private CmsRepositoryLookup repoLookup;
	private FilexmlCommit commit;
	private TransformerServiceFactory transformerServiceFactory;
	private XmlSourceReaderS9api sourceReader;

	private TransformService transformService;

	private static final String transformTestDoc = "x-svn:///svn/repo1/doc/transform-test.xml";
	private static final String transformTestMap = "x-svn:///svn/repo1/doc/transform-test-map.xml";
	private static final String transformTestDocStrict = "x-svn:///svn/repo1/strict/transform-test.xml";

	@Before
	public void setup() throws Exception {
		testSetUp = new TestFileXmlSetUp(new CmsRepository("http://cmshostname:123/svn/repo1"),
				new FilexmlSourceClasspath("se/simonsoft/cms/transform/datasets/repo1"));
		repo = testSetUp.getRepo();
		commit = testSetUp.getCommit();
		indexing = testSetUp.getIndexing();
		lookup = indexing.getContext().getInstance(CmsItemLookup.class);
		repoLookup = indexing.getContext().getInstance(CmsRepositoryLookup.class);
		transformerServiceFactory = indexing.getContext().getInstance(TransformerServiceFactory.class);
		sourceReader = indexing.getContext().getInstance(XmlSourceReaderS9api.class);
		Processor processor = indexing.getContext().getInstance(Processor.class);

		transformService = new TransformServiceXsl(commit, lookup, null, repoLookup, transformerServiceFactory, sourceReader, null, new TestTransformSchemaService(processor));
	}

	@After
	public void tearDown() {
		indexing.tearDown();
	}

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
		assertEquals("nothing committed", youngest, repoLookup.getYoungest(repo).getNumber());
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
	public void testSchemaVariantFromRootElement() {
		assertEquals("ditamap", TransformServiceXsl.getSchemaVariant("map"));
		assertEquals("ditamap", TransformServiceXsl.getSchemaVariant("techdocmap"));
		assertEquals("ditamap", TransformServiceXsl.getSchemaVariant("keydefmap"));
		assertEquals("ditamap", TransformServiceXsl.getSchemaVariant("bookmap"));
		assertEquals("ditabase", TransformServiceXsl.getSchemaVariant("topic"));
		assertEquals("ditabase", TransformServiceXsl.getSchemaVariant("task"));
		assertEquals("ditabase", TransformServiceXsl.getSchemaVariant("dita"));
		assertEquals("ditabase", TransformServiceXsl.getSchemaVariant("document"));
		assertEquals("ditabase", TransformServiceXsl.getSchemaVariant(null));
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
		} catch (IllegalStateException e) {
			// 3 x para in the topic, reltable in the map.
			assertEquals("Schema validation failed: 2 of 2 items, 4 issues", e.getMessage());
		}
		assertNotCommittedAndUnlocked(youngest);
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
		} catch (IllegalStateException e) {
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
		} catch (IllegalStateException e) {
			assertEquals("Schema validation failed: 1 of 2 items, 1 issues", e.getMessage());
		}
		assertEquals("nothing committed", youngest, repoLookup.getYoungest(repo).getNumber());
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
		} catch (IllegalStateException e) {
			assertTrue(e.getMessage(), e.getMessage().startsWith("Schema validation failed: 1 of 2 items, "));
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
		TransformService transformServiceNoSchema = new TransformServiceXsl(commit, lookup, null, repoLookup, transformerServiceFactory, sourceReader, null, null);
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
		params.put("pathext", "xml");
		params.put("SchemaValidate", "true");
		TransformImportOptions options = new TransformImportOptions();
		options.setParams(params);

		options.setContent("<techdocmap><reltable/></techdocmap>");
		try {
			transformService.importItem(folder, options);
			fail("Should fail schema validation");
		} catch (IllegalStateException e) {
			assertEquals("Schema validation failed: 1 of 1 items, 1 issues", e.getMessage());
		}
		assertEquals("nothing committed", youngest, repoLookup.getYoungest(repo).getNumber());

		options.setContent("<techdocmap><title>Imported</title></techdocmap>");
		Set<CmsItemId> result = transformService.importItem(folder, options);
		assertEquals(1, result.size());
		assertEquals(youngest + 1, repoLookup.getYoungest(repo).getNumber());
	}
}
