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
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import se.repos.testing.indexing.ReposTestIndexing;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.testconfig.TestFileXmlSetUp;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXslTest {

	private static ReposTestIndexing indexing = null;

	private static TransformService transformService;
	private static CmsItemLookup lookup;
	private static FilexmlCommit commit;

	static final String hostname = "cmshostname:123"; //Port number has at times been a source of confusion when there are multiple issues at play.

	static CmsRepository repo;

	private static TestFileXmlSetUp testSetUp;

	private static CmsRepositoryLookup repoLookup;

	static Long startRev = new Long(1); // Defined as youngest in the filexml repo.

	static final String transformTestDoc = "x-svn:///svn/repo1^/doc/transform-test.xml";

	@Before
	public void setup() throws Exception {
		testSetUp = new TestFileXmlSetUp(new CmsRepository("http://" + hostname + "/svn/repo1"),
				new FilexmlSourceClasspath("se/simonsoft/cms/transform/datasets/repo1"));
		repo = testSetUp.getRepo();
		commit = testSetUp.getCommit();
		indexing = testSetUp.getIndexing();
		lookup = indexing.getContext().getInstance(CmsItemLookup.class);
		repoLookup = indexing.getContext().getInstance(CmsRepositoryLookup.class);
		TransformerServiceFactory transformerServiceFactory = indexing.getContext().getInstance(TransformerServiceFactory.class);
		XmlSourceReaderS9api sourceReader = indexing.getContext().getInstance(XmlSourceReaderS9api.class);
		
		Map<String, Source> services = new HashMap<>();
		StreamSource streamSourceMulti = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-multiple-output.xsl"));
		StreamSource streamSourceSingle = new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-single-output.xsl"));
		
		services.put("transform-multiple-output.xsl", streamSourceMulti);
		services.put("transform-single-output.xsl", streamSourceSingle);
		
		transformService = new TransformServiceXsl(commit, lookup, repoLookup, transformerServiceFactory, services, sourceReader); // may exist a injected version. 
	}

	@After
	public void tearDown() {
		indexing.tearDown();
	}

	@Test
	public void testSingleOutputFolderDoNotExist() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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
			transformService.transform(item, config);
			fail("Should fail. Output is specified but the folder does not exsist.");
		} catch (IllegalArgumentException e) {
			assertEquals("Specified output must be an existing folder: /xml/nonexisting", e.getMessage());
		}
	}

	@Test
	public void testSingleOutputFolderDefaultOverwriteTrue() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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

		transformService.transform(item, config);
		
		CmsItem itemNew = lookup.getItem(itemId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("single-output=\"true\""));

	}

	@Test
	public void testSingleOutputFolderDefaultOverwriteFalse() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is not set.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items.", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items.", e.getMessage());
		}
	}

	@Test
	public void testSingleOutputFolderExists() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc).withPegRev(1L);
		CmsItem item = lookup.getItem(itemId);

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
			transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			fail("Output folder is valid. Should not throw exception: " + e.getMessage());
		}
		
		CmsItemId itemIdNew = new CmsItemIdArg(repo, new CmsItemPath(optionsParams.get("output")).append(itemId.getRelPath().getName()));
		CmsItem itemNew = lookup.getItem(itemIdNew);
		
		CmsItemProperties properties = itemNew.getProperties();
		assertEquals("x-svn:///svn/repo1^/doc/transform-test.xml", properties.getString("abx:TransformBase"));
		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("single-output=\"true\""));
	}
	
	@Test
	public void testMultipleOutputFolderDoNotExist() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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
			transformService.transform(item, config);
			fail("Should fail. Output is specified but the folder does not exsist.");
		} catch (IllegalArgumentException e) {
			assertEquals("Specified output must be an existing folder: /xml/nonexisting", e.getMessage());
		}
	}

	@Test
	public void testMultipleOutputFolderDefaultOverwriteTrue() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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

		transformService.transform(item, config);
		
		CmsItem itemNew = lookup.getItem(itemId);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("multiple-output=\"true\""));
		
		CmsItemId sec1Id = new CmsItemIdArg(repo, new CmsItemPath(itemId.getRelPath().getParent().getPath().concat("/sections/section1.xml")));
		CmsItem sec1Item = lookup.getItem(sec1Id);
		
		assertEquals(itemId.getLogicalId(), sec1Item.getProperties().getString("abx:TransformBase"));
		
		ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
		sec1Item.getContents(baos1);

		String sect1Str = baos1.toString(StandardCharsets.UTF_8.name());
		assertTrue(sect1Str.contains("multiple-output=\"true\""));
		assertTrue(sect1Str.contains("name=\"section1.xml\""));
		
		CmsItemId sec2Id = new CmsItemIdArg(repo, new CmsItemPath(itemId.getRelPath().getParent().getPath().concat("/sections/section2.xml")));
		CmsItem sec2Item = lookup.getItem(sec2Id);
		
		assertEquals(itemId.getLogicalId(), sec2Item.getProperties().getString("abx:TransformBase"));
		
		ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
		sec2Item.getContents(baos2);

		String sec2Str = baos2.toString(StandardCharsets.UTF_8.name());
		assertTrue(sec2Str.contains("multiple-output=\"true\""));
		assertTrue(sec2Str.contains("name=\"section2.xml\""));
	}

	@Test
	public void testMultipleOutputFolderDefaultOverwriteFalse() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items.", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Item already exists, config prohibiting overwrite of existing items.", e.getMessage());
		}
	}

	@Test
	public void testMultipleOutputAllItemsExists() throws Exception {
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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

		transformService.transform(item, config);
		
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
	public void testTransformerServiceCmsBuiltIn() throws Exception {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

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

		transformService.transform(item, config);
		
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
		CmsItem item = lookup.getItem(itemId);
		
		try {
			transformService.transform(item, null);
		} catch (IllegalArgumentException e) {
			assertEquals("TransformServiceXsl needs a valid TransformConfig object.", e.getMessage());
		}

		TransformConfig config = new TransformConfig();
		config.setActive(true);
		
		try {
			transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			assertEquals("TransformServiceXsl needs a valid TransformConfig object.", e.getMessage());
		}
	}
	
	@Test
	public void testMissingTransformerService() throws Exception {
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);
		
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
			transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			assertEquals("Could not find source with stylesheet name: non-existing.xsl", e.getMessage());
		}
		
		optionsParams.put("stylesheet", "/non/existing.xsl");
		
		try {
			transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			assertEquals("Specified stylesheet does not exist at path: /non/existing.xsl", e.getMessage());
		}
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
			transformService.transform(item, config);
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
	
	@Test
	@Ignore
	public void testCommentIsVelocityString() throws Exception {
		// Future implementation. Comment may be a velocity formated string.
	}

}
