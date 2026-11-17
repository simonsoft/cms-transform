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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.repos.testing.indexing.ReposTestIndexing;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.naming.CmsItemNameFactory;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.testconfig.TestFileXmlSetUp;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXslTest {

	private static ReposTestIndexing indexing = null;

	private static TransformService transformService;
	private static CmsItemLookup lookup;
	private static CmsItemLookupReporting lookupReporting;
	private static FilexmlCommit commit;

	static final String hostname = "cmshostname:123"; //Port number has at times been a source of confusion when there are multiple issues at play.

	static CmsRepository repo;

	private static TestFileXmlSetUp testSetUp;

	private static CmsRepositoryLookup repoLookup;

	static Long startRev = new Long(1); // Defined as youngest in the filexml repo.

	static final String transformTestDoc = "x-svn:///svn/repo1/doc/transform-test.xml";

	@Before
	public void setup() throws Exception {
		testSetUp = new TestFileXmlSetUp(new CmsRepository("http://" + hostname + "/svn/repo1"),
				new FilexmlSourceClasspath("se/simonsoft/cms/transform/datasets/repo1"));
		repo = testSetUp.getRepo();
		commit = testSetUp.getCommit();
		indexing = testSetUp.getIndexing();
		lookup = indexing.getContext().getInstance(CmsItemLookup.class);
		lookupReporting = null;
		repoLookup = indexing.getContext().getInstance(CmsRepositoryLookup.class);
		TransformerServiceFactory transformerServiceFactory = indexing.getContext().getInstance(TransformerServiceFactory.class);
		XmlSourceReaderS9api sourceReader = indexing.getContext().getInstance(XmlSourceReaderS9api.class);
		//Map<CmsRepository, CmsItemLookup> lookupMap = new HashMap<>();
		//lookupMap.put(repo, lookup);
		//(CmsItemNameFactory nameFactory = new CmsItemNameFactory(lookupMap);
		CmsItemNameFactory nameFactory = null;

		transformService = new TransformServiceXsl(commit, lookup, lookupReporting, repoLookup, transformerServiceFactory, sourceReader, nameFactory, null); // may exist a injected version. 
	}
	

	@After
	public void tearDown() {
		indexing.tearDown();
	}

	@Test
	public void testSingleOutputFolderDoNotExist() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("output", "/xml/nonexisting");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
			fail("Should fail. Output is specified but the folder does not exsist.");
		} catch (IllegalArgumentException e) {
			assertEquals("Specified output must be an existing folder: /xml/nonexisting", e.getMessage());
		}
	}

	@Test
	public void testSingleOutputFolderDefaultOverwriteTrue() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		CmsItem itemNew = lookup.getItem(itemId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("single-output=\"true\""));

	}

	@Test
	public void testSingleOutputFolderDefaultOverwriteFalse() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		//No overwrite param set defaults to false.
		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
			fail("Should fail, item already exist but overwrite is not set.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items: /doc/transform-test.xml", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(itemId, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items: /doc/transform-test.xml", e.getMessage());
		}
	}

	@Test
	public void testSingleOutputFolderExists() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc).withPegRev(1L);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("output", "/transformed/single");
		optionsParams.put("overwrite", "");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
		} catch (IllegalArgumentException e) {
			fail("Output folder is valid. Should not throw exception: " + e.getMessage());
		}
		
		CmsItemId itemIdNew = new CmsItemIdArg(repo, new CmsItemPath(optionsParams.get("output")).append(itemId.getRelPath().getName()));
		CmsItem itemNew = lookup.getItem(itemIdNew);
		
		CmsItemProperties properties = itemNew.getProperties();
		assertEquals("x-svn:///svn/repo1/doc/transform-test.xml", properties.getString("abx:TransformBase"));
		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("single-output=\"true\""));
	}

	@Test
	public void testApplyRevisionPropertiesValid() {
		CmsPatchset patchset = new CmsPatchset(repo, repoLookup.getYoungest(repo));
		Map<String, String> revprops = new HashMap<String, String>();
		revprops.put("cms:Move", "true");
		revprops.put("abx:Test", "value");

		((TransformServiceXsl) transformService).applyRevisionProperties(patchset, revprops);

		assertEquals("true", patchset.getRevisionProperties().getString("cms:Move"));
		assertEquals("value", patchset.getRevisionProperties().getString("abx:Test"));
	}

	@Test
	public void testApplyRevisionPropertiesNullIsNoop() {
		CmsPatchset patchset = new CmsPatchset(repo, repoLookup.getYoungest(repo));

		((TransformServiceXsl) transformService).applyRevisionProperties(patchset, null);

		assertTrue(patchset.getRevisionProperties().getKeySet().isEmpty());
	}

	@Test
	public void testApplyRevisionPropertiesInvalidKeyNoColon() {
		CmsPatchset patchset = new CmsPatchset(repo, repoLookup.getYoungest(repo));
		Map<String, String> revprops = new HashMap<String, String>();
		revprops.put("InvalidKeyNoColon", "value");

		try {
			((TransformServiceXsl) transformService).applyRevisionProperties(patchset, revprops);
			fail("Should fail, revprop key must have the form 'prefix:name'.");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid revprop key: 'InvalidKeyNoColon'. Must have the form 'prefix:name' (exactly one colon), e.g. 'cms:Move'.", e.getMessage());
		}
		assertTrue("No revprops should have been set when validation fails.", patchset.getRevisionProperties().getKeySet().isEmpty());
	}

	@Test
	public void testApplyRevisionPropertiesInvalidKeyEmptyPrefix() {
		CmsPatchset patchset = new CmsPatchset(repo, repoLookup.getYoungest(repo));
		Map<String, String> revprops = new HashMap<String, String>();
		revprops.put(":name", "value");

		try {
			((TransformServiceXsl) transformService).applyRevisionProperties(patchset, revprops);
			fail("Should fail, revprop key must have a non-empty prefix.");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid revprop key: ':name'. Must have the form 'prefix:name' (exactly one colon), e.g. 'cms:Move'.", e.getMessage());
		}
	}

	@Test
	public void testRevisionPropertiesInvalidKeyAbortsTransformBeforeCommit() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		Map<String, String> revprops = new HashMap<String, String>();
		revprops.put("InvalidKeyNoColon", "value");
		configOptions.setRevprops(revprops);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
			fail("Should fail, revprop key must have the form 'prefix:name'.");
		} catch (IllegalArgumentException e) {
			assertEquals("Invalid revprop key: 'InvalidKeyNoColon'. Must have the form 'prefix:name' (exactly one colon), e.g. 'cms:Move'.", e.getMessage());
		}
	}

	@Test
	public void testRevisionPropertiesValidDoesNotBreakTransform() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-single-output.xsl");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		Map<String, String> revprops = new HashMap<String, String>();
		revprops.put("cms:Move", "true");
		configOptions.setRevprops(revprops);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);

		CmsItem itemNew = lookup.getItem(itemId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("single-output=\"true\""));
	}

	@Test
	public void testMultipleOutputFolderDoNotExist() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("output", "/xml/nonexisting");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
			fail("Should fail. Output is specified but the folder does not exsist.");
		} catch (IllegalArgumentException e) {
			assertEquals("Specified output must be an existing folder: /xml/nonexisting", e.getMessage());
		}
	}
	
	@Test
	public void testLongTruncatedComment() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc).withPegRev(1L);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("output", "/transformed/multiple");
		optionsParams.put("overwrite", "");
		optionsParams.put("comment", getCommentTwoThousandBytesPlus());
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		CmsItemId itemIdNew = new CmsItemIdArg(repo, new CmsItemPath(optionsParams.get("output")).append(itemId.getRelPath().getName()));
		CmsItem itemNew = lookup.getItem(itemIdNew);
		
		CmsItemProperties revisionProperties = commit.getCmsContentsReader().getRevisionProperties(itemNew.getRevisionChanged());
		String history = revisionProperties.getString("svn:log");
		assertTrue(history.startsWith("Lorem ipsum dolor sit amet"));
		assertTrue(history.endsWith("..."));
		
	}

	@Test
	public void testMultipleOutputFolderDefaultOverwriteTrue() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Overwrite transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		CmsItem itemNew = lookup.getItem(itemId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("multiple-output=\"true\""));
		assertTrue("Primary output should have DOCTYPE decl", string.contains("DOCTYPE"));
		assertTrue("Primary output should have DOCTYPE decl", string.contains("PRIMARY"));
		
		CmsItemId sec1Id = new CmsItemIdArg(repo, new CmsItemPath(itemId.getRelPath().getParent().getPath().concat("/sections/section1.xml")));
		CmsItem sec1Item = lookup.getItem(sec1Id);
		
		assertEquals(itemId.getLogicalId(), sec1Item.getProperties().getString("abx:TransformBase"));
		
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		sec1Item.getContents(baos1);

		String sect1Str = baos1.toString(StandardCharsets.UTF_8.name());
		assertTrue(sect1Str.contains("multiple-output=\"true\""));
		assertTrue(sect1Str.contains("name=\"section1.xml\""));
		assertTrue("Multiple output should have DOCTYPE decl", sect1Str.contains("DOCTYPE"));
		assertTrue("Multiple output should have DOCTYPE decl", sect1Str.contains("MULTIPLE"));
		assertFalse("Should clean up temporary doctype attrs", sect1Str.contains("cms:doctype-public"));
		assertFalse("Should clean up temporary doctype attrs", sect1Str.contains("cms:doctype-system"));
		
		CmsItemId sec2Id = new CmsItemIdArg(repo, new CmsItemPath(itemId.getRelPath().getParent().getPath().concat("/sections/section2.xml")));
		CmsItem sec2Item = lookup.getItem(sec2Id);
		
		assertEquals(itemId.getLogicalId(), sec2Item.getProperties().getString("abx:TransformBase"));
		
		CmsItemProperties revisionProperties = commit.getCmsContentsReader().getRevisionProperties(sec2Item.getRevisionChanged());
		String history = revisionProperties.getString("svn:log");
		assertTrue(history.contains("Overwrite transform!"));
		assertTrue(history.contains("Transform multiple output"));
		
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		sec2Item.getContents(baos2);

		String sec2Str = baos2.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec2Str.contains("multiple-output=\"true\""));
		assertTrue(sec2Str.contains("name=\"section2.xml\""));
	}

	@Test
	public void testMultipleOutputFolderDefaultOverwriteFalse() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		//No overwrite param set defaults to false.
		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		try {
			transformService.transform(itemId, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items: /doc/transform-test.xml", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(itemId, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items: /doc/transform-test.xml", e.getMessage());
		}
	}

	@Test
	public void testMultipleOutputAllItemsExists() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("output", "/transformed/multiple/existing");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		String outputPath = optionsParams.get("output");
		
		CmsItemId sec1Id = new CmsItemIdArg(repo, new CmsItemPath(outputPath.concat("/sections/section1.xml")));
		CmsItem sec1Item = lookup.getItem(sec1Id.withPegRev(2L));
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		sec1Item.getContents(baos1);

		String sec1Str = baos1.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec1Str.contains("multiple-output=\"true\""));
		assertTrue(sec1Str.contains("name=\"section1.xml\""));
		
		CmsItemId sec2Id = new CmsItemIdArg(repo, new CmsItemPath(outputPath.concat("/sections/section2.xml")));
		CmsItem sec2Item = lookup.getItem(sec2Id.withPegRev(2L));
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		sec2Item.getContents(baos2);

		String sec2Str = baos2.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec2Str.contains("multiple-output=\"true\""));
		assertTrue(sec2Str.contains("name=\"section2.xml\""));
	}
	
	@Test
	public void testOutputWithEncodedHref() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("output", "/transformed/multiple/existing");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		String outputPath = optionsParams.get("output");
		
		// section3.xml path should have been url decoded (ö should be ö, the space should have been preserved and %20 should be decoded to space).
		// It is technically incorrect to send non-encoded string to result-document href. 
		CmsItemId sec3Id = new CmsItemIdArg(repo, new CmsItemPath(outputPath.concat("/sections/földer space/folder encoded/section3.xml")));
		CmsItem sec3Item = lookup.getItem(sec3Id.withPegRev(2L));
		ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
		sec3Item.getContents(baos3);
		String sec3Str = baos3.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec3Str.contains("multiple-output=\"true\""));
		assertTrue(sec3Str.contains("name=\"földer space/folder%20encoded/section3.xml\""));
	}

	@Test
	public void testTransformerServiceCmsBuiltIn() throws Exception {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "transform-multiple-output.xsl");
		optionsParams.put("output", "/transformed/multiple");
		optionsParams.put("overwrite", "");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		transformService.transform(itemId, config);
		
		String outputPath = optionsParams.get("output");
		
		CmsItemId sec1Id = new CmsItemIdArg(repo, new CmsItemPath(outputPath.concat("/sections/section1.xml")));
		CmsItem sec1Item = lookup.getItem(sec1Id);
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		sec1Item.getContents(baos1);

		String sect1Str = baos1.toString(StandardCharsets.UTF_8.name());
		assertTrue(sect1Str.contains("multiple-output=\"true\""));
		assertTrue(sect1Str.contains("name=\"section1.xml\""));
		
		CmsItemId sec2Id = new CmsItemIdArg(repo, new CmsItemPath(outputPath.concat("/sections/section2.xml")));
		CmsItem sec2Item = lookup.getItem(sec2Id);
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		sec2Item.getContents(baos2);

		String sec2Str = baos2.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec2Str.contains("multiple-output=\"true\""));
		assertTrue(sec2Str.contains("name=\"section2.xml\""));
	}


	@Test
	public void testInvalidConfig() throws Exception {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		
		try {
			transformService.transform(itemId, null);
		} catch (IllegalArgumentException e) {
			assertEquals("TransformServiceXsl needs a valid TransformConfig object.", e.getMessage());
		}

		TransformConfig config = new TransformConfig();
		config.setActive(true);
		
		try {
			transformService.transform(itemId, config);
		} catch (IllegalArgumentException e) {
			assertEquals("TransformServiceXsl needs a valid TransformConfig object.", e.getMessage());
		}
	}
	
	@Test
	public void testMissingTransformerService() throws Exception {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		
		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "non-existing.xsl");
		optionsParams.put("output", "/transformed/multiple");
		optionsParams.put("overwrite", "");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);
		
		try {
			transformService.transform(itemId, config);
		} catch (IllegalArgumentException e) {
			assertEquals("No built-in stylesheet named: non-existing.xsl", e.getMessage());
		}
		
		optionsParams.put("stylesheet", "/non/existing.xsl");
		
		try {
			transformService.transform(itemId, config);
		} catch (IllegalArgumentException e) {
			assertEquals("Specified stylesheet does not exist at path: /non/existing.xsl", e.getMessage());
		}
	}
	
	@Test
	public void testTextOutput() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-text-output.xsl");
		optionsParams.put("output", "");
		optionsParams.put("comment", "Text output");
		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");
		configOptions.setParams(optionsParams);
		TransformConfig config = new TransformConfig();
		config.setActive(true);
		config.setOptions(configOptions);

		transformService.transform(itemId, config);

		CmsItemId textId = new CmsItemIdArg(repo, new CmsItemPath("/doc/scripts/transform-test.txt"));
		CmsItem textItem = lookup.getItem(textId);
		assertEquals(itemId.getLogicalId(), textItem.getProperties().getString("abx:TransformBase"));
		String text = getContents(textId);
		assertEquals("Text output must not be XML serialized (no declaration, no escaping)",
				"# Script with one line per section\n"
				+ "svn propset meta:sample 'section1.xml' <path> &\n"
				+ "svn propset meta:sample 'section2.xml' <path> &\n"
				+ "svn propset meta:sample 'földer space/folder%20encoded/section3.xml' <path> &\n",
				text);

		CmsItemId noRootId = new CmsItemIdArg(repo, new CmsItemPath("/doc/scripts/no-root.xml"));
		assertFalse("Result document without root element should be discarded", pathExists(noRootId));
	}

	@Test
	public void testTextOutputFolderConcatenated() throws Exception {
		// Each item in the folder produces the same text result-document href.
		CmsItemId folderId = new CmsItemIdArg("x-svn:///svn/repo1/transformed/multiple/existing");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-text-output.xsl");
		optionsParams.put("output", "");
		optionsParams.put("comment", "Text output per folder");
		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");
		configOptions.setParams(optionsParams);
		TransformConfig config = new TransformConfig();
		config.setActive(true);
		config.setOptions(configOptions);

		transformService.transform(folderId, config);

		CmsItemId textId = new CmsItemIdArg(repo, new CmsItemPath("/doc/scripts/transform-test.txt"));
		assertEquals("Folder is the transform base of the concatenated output", folderId.getLogicalId(), lookup.getItem(textId).getProperties().getString("abx:TransformBase"));
		String text = getContents(textId);
		assertEquals("One text output per item, concatenated: " + text, 4, text.split("# Script with one line per section", -1).length - 1);
		assertEquals("Sections of all items: " + text, 3 + 2, text.split("svn propset", -1).length - 1);
	}

	private boolean pathExists(CmsItemId itemId) {
		try {
			lookup.getItem(itemId);
			return true;
		} catch (se.simonsoft.cms.item.info.CmsItemNotFoundException e) {
			return false;
		}
	}

	@Test
	public void testEmptyExceptDeclaration() {
		assertTrue(TransformServiceXsl.emptyExceptDeclaration("<?xml version=\"1.0\"?>\n "));
		assertTrue(TransformServiceXsl.emptyExceptDeclaration(" \n"));
		assertFalse(TransformServiceXsl.emptyExceptDeclaration("<?xml version=\"1.0\"?><a/>"));
		assertFalse("Text without declaration", TransformServiceXsl.emptyExceptDeclaration("a"));
	}

	@Test
	public void testEmptyTransformWillBeDiscarded() throws UnsupportedEncodingException {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);
		
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		item.getContents(baos1);

		String stringOrignalItem = baos1.toString(StandardCharsets.UTF_8.name());
		
		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-no-output.xsl");
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);
		
		try {
			transformService.transform(itemId, config);
			fail("Should result in empty changeset and backend-filexml will not accept that.");
		} catch (IllegalArgumentException e) {
			assertEquals("Filexml backend does not support empty changeset (patchset resulted in empty changeset).", e.getMessage());
		}
		CmsItem itemNew = lookup.getItem(itemId);
		
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		itemNew.getContents(baos2);

		String stringNewContent = baos2.toString(StandardCharsets.UTF_8.name());
		
		assertEquals(stringOrignalItem, stringNewContent);
		
	}
	
	private TransformConfig getConfigDryRunOverwrite(String stylesheet) {
		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", stylesheet);
		optionsParams.put("overwrite", "true");
		optionsParams.put("comment", "Dry run transform");
		optionsParams.put("dryrun", "true");

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");
		configOptions.setParams(optionsParams);
		Map<String, String> revprops = new HashMap<>();
		revprops.put("cms:operation", "move");
		configOptions.setRevprops(revprops);

		TransformConfig config = new TransformConfig();
		config.setActive(true);
		config.setOptions(configOptions);
		return config;
	}

	private String getContents(CmsItemId itemId) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		lookup.getItem(itemId).getContents(baos);
		return baos.toString(StandardCharsets.UTF_8.name());
	}

	@Test
	public void testDryRunMultipleOutputOverwrite() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		RepoRevision youngest = repoLookup.getYoungest(repo);
		String contentsBefore = getContents(itemId);

		TransformResult result = transformService.transform(itemId, getConfigDryRunOverwrite("/stylesheet/transform-multiple-output.xsl"));

		assertTrue(result.isDryRun());
		assertNull(result.getRevision());
		assertEquals(youngest.getNumber(), result.getBaseRevision().getNumber());
		assertEquals(List.of(new CmsItemPath("/doc/transform-test.xml")), result.getModified());
		assertTrue(result.getAdded().toString(), result.getAdded().contains(new CmsItemPath("/doc/sections/section1.xml")));
		assertTrue(result.getAdded().toString(), result.getAdded().contains(new CmsItemPath("/doc/sections/section2.xml")));

		assertEquals("nothing committed", youngest.getNumber(), repoLookup.getYoungest(repo).getNumber());
		assertEquals("unchanged", contentsBefore, getContents(itemId));
		assertNull("not locked", lookup.getLocked(itemId));
		try {
			lookup.getItem(new CmsItemIdArg(repo, new CmsItemPath("/doc/sections/section1.xml")));
			fail("Should not be added in a dry run.");
		} catch (CmsItemNotFoundException e) {
			// expected
		}

		JsonNode json = new ObjectMapper().readTree(result.toJson(new ObjectMapper().writer(), TransformResult.JSON_MAX_LENGTH));
		assertTrue(json.get("dryrun").asBoolean());
		assertFalse(json.has("revision"));
		assertEquals(1, json.get("modifiedCount").asInt());
		assertEquals("/doc/transform-test.xml", json.get("modified").get(0).asText());
	}

	@Test
	public void testDryRunFailsOnExistingLock() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		RepoRevision youngest = repoLookup.getYoungest(repo);
		CmsItemLock lock = commit.lock("Editing", youngest, itemId.getRelPath()).getSingle();
		assertNotNull(lock);

		TransformDryRunException e = assertThrows(TransformDryRunException.class,
				() -> transformService.transform(itemId, getConfigDryRunOverwrite("/stylesheet/transform-single-output.xsl")));

		assertEquals("Dry run failed: 1 items locked", e.getMessage());
		assertEquals("TransformLocked", e.getErrorName());
		assertNull(e.getValidation());
		CmsItemLock reported = e.getLocked().get(new CmsItemPath("/doc/transform-test.xml"));
		assertNotNull(reported);
		assertEquals(lock.getToken(), reported.getToken());
		assertEquals("Editing", reported.getComment());
		assertEquals("nothing committed", youngest.getNumber(), repoLookup.getYoungest(repo).getNumber());
		assertEquals("existing lock is kept", lock.getToken(), lookup.getLocked(itemId).getToken());

		JsonNode json = new ObjectMapper().readTree(e.toJson(new ObjectMapper().writer(), TransformResult.JSON_MAX_LENGTH));
		assertEquals("Dry run failed: 1 items locked", json.get("message").asText());
		assertTrue(json.get("dryrun").asBoolean());
		assertFalse("no validation requested", json.has("issues"));
		assertEquals(1, json.get("lockedCount").asInt());
		assertEquals("/doc/transform-test.xml", json.get("locked").get(0).get("path").asText());
		assertEquals("Editing", json.get("locked").get(0).get("comment").asText());
		assertEquals(0, json.get("lockedOmitted").asInt());
	}

	@Test
	public void testDryRunInvalidRevpropFails() throws Exception {
		TransformConfig config = getConfigDryRunOverwrite("/stylesheet/transform-single-output.xsl");
		config.getOptions().getRevprops().put("InvalidKeyNoColon", "x");
		try {
			transformService.transform(new CmsItemIdArg(transformTestDoc), config);
			fail("Should fail on the revprop key, same as without dryrun.");
		} catch (IllegalArgumentException e) {
			assertTrue(e.getMessage(), e.getMessage().startsWith("Invalid revprop key: 'InvalidKeyNoColon'"));
		}
	}

	@Test
	@Ignore
	public void testCommentIsVelocityString() throws Exception {
		// Future implementation. Comment may be a velocity formated string.
	}
	
	private String getCommentTwoThousandBytesPlus() {
		return "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Integer non vulputate orci. Curabitur dapibus posuere placerat. Nunc dui velit, iaculis nec nulla non, mollis dapibus diam. Praesent suscipit velit sit amet mauris pellentesque commodo ac sed odio. Pellentesque habitant morbi tristique senectus et netus et malesuada fames ac turpis egestas. Donec eleifend diam ut nunc accumsan, at consectetur tortor aliquam. Suspendisse convallis odio velit, eu bibendum dolor auctor in. Proin et egestas mi. Vivamus rhoncus lacus sed arcu pretium aliquet. Fusce congue volutpat odio nec ultrices. Cras tellus turpis, pulvinar eu felis auctor, tincidunt egestas nisl. Donec placerat, ex sit amet vestibulum vestibulum, ante mi pellentesque est, a porta dolor sem sit amet arcu.\n" + 
				"\n" + 
				"Donec faucibus nisi nisl, vitae maximus nibh sodales ut. Donec et ornare nisl. Integer tincidunt lorem vel dolor porttitor vestibulum. Interdum et malesuada fames ac ante ipsum primis in faucibus. Integer semper ac dui ut mollis. Cras ultricies vehicula nibh, in pharetra sem. Morbi sollicitudin risus sit amet convallis blandit. Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus commodo non augue a feugiat. Integer sit amet diam ligula. Nulla sagittis, nisi in suscipit hendrerit, tellus neque placerat elit, sed imperdiet tortor urna nec ex.\n" + 
				"\n" + 
				"Vivamus pellentesque tincidunt erat, sit amet sodales est commodo rutrum. Integer ullamcorper finibus velit, ut posuere lorem malesuada vel. Vestibulum non elementum neque, et tempor risus. Praesent eu cursus felis, ut egestas velit. Proin est augue, dapibus in leo vel, congue dignissim nibh. Suspendisse tempus leo ligula, quis pharetra odio tempus ac. Praesent at elementum felis. Donec quis libero elit. Etiam ultricies purus urna, ac scelerisque ante euismod nec. Maecenas blandit tortor quam, ac venenatis magna commodo vel. Vestibulum aliquam turpis orci, ut pretium erat tempus vel.\n" + 
				"\n" + 
				"Mauris faucibus, elit dictum tempor viverra, est tortor faucibus sapien, eget volutpat nulla turpis scelerisque sem. Donec non posuere libero, ac blandit eros. Fusce semper mauris non ultricies gravida. Aenean molestie vel nunc eu porta. Integer suscipit convallis sapien sed commodo. Nunc maximus nisl a varius semper. Nulla molestie arcu magna, nec ultricies magna eleifend amet.";
	}

}
