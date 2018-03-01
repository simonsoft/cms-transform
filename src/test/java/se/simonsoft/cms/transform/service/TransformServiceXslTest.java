package se.simonsoft.cms.transform.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import se.repos.testing.indexing.ReposTestIndexing;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.testconfig.TestFileXmlSetUp;
import se.simonsoft.cms.xmlsource.handler.XmlSourceReader;
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

	static final Long startRev = new Long(191); // Defined as youngest in the filexml repo.

	static final String transformTestDoc = "x-svn:///svn/repo1^/doc/transform-test.xml";

	@BeforeClass
	public static void setup() throws Exception {
		testSetUp = new TestFileXmlSetUp(new CmsRepository("http://" + hostname + "/svn/repo1"),
				new FilexmlSourceClasspath("se/simonsoft/cms/transform/datasets/repo1"));
		repo = testSetUp.getRepo();
		commit = testSetUp.getCommit();
		indexing = testSetUp.getIndexing();
		lookup = indexing.getContext().getInstance(CmsItemLookup.class);
		repoLookup = indexing.getContext().getInstance(CmsRepositoryLookup.class);
		TransformerServiceFactory s = indexing.getContext().getInstance(TransformerServiceFactory.class);
		XmlSourceReader sourceReader = indexing.getContext().getInstance(XmlSourceReader.class);
		transformService = new TransformServiceXsl(commit, lookup, repoLookup, s, null, sourceReader); // may exist a injected version. 
	}

	@AfterClass
	public static void tearDown() {
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
			assertEquals("Output folder '/xml/nonexisting' does not exist in repo 'repo1'.", e.getMessage());
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

		RepoRevision rev = null;
		rev = transformService.transform(item, config);

		CmsItem itemNew = lookup.getItem(itemId.withPegRev(rev.getNumber()));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		System.out.println("transformed!!: " +  string);
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
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Attempt to Add file on existing path: /doc/transform-test.xml", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Attempt to Add file on existing path: /doc/transform-test.xml", e.getMessage());
		}
	}

	@Test
	public void testSingleOutputFolderExists() throws Exception {

		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
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

		RepoRevision rev = null;
		try {
			rev = transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			fail("Output folder is valid. Should not throw excepetion: " + e.getMessage());
		}

		CmsItemId itemIdNew = new CmsItemIdArg(repo, new CmsItemPath(optionsParams.get("output")).append(itemId.getRelPath().getName()));
		CmsItem itemNew = lookup.getItem(itemIdNew);
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
			assertEquals("Output folder '/xml/nonexisting' does not exist in repo 'repo1'.", e.getMessage());
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
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		RepoRevision rev = null;
		rev = transformService.transform(item, config);

		CmsItem itemNew = lookup.getItem(itemId.withPegRev(rev.getNumber()));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		System.out.println("transformed!!: " +  string);
		assertTrue(string.contains("multiple-output=\"true\""));
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
			assertEquals("Attempt to Add file on existing path: /doc/transform-test.xml", e.getMessage());
		}

		//Adding overwrite false to config to test when value is set.
		optionsParams.put("overwrite", "false"); // No overwrites is allowed.

		try {
			transformService.transform(item, config);
			fail("Should fail, item already exist but overwrite is set to false.");
		} catch(IllegalStateException e) {
			assertEquals("Attempt to Add file on existing path: /doc/transform-test.xml", e.getMessage());
		}
	}

	@Test
	public void testMultipleOutputFolderExists() throws Exception {

		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);

		TransformConfig config = new TransformConfig();
		config.setActive(true);

		TransformConfigOptions configOptions = new TransformConfigOptions();
		configOptions.setType("xsl");

		Map<String, String> optionsParams = new HashMap<String, String>();
		optionsParams.put("stylesheet", "/stylesheet/transform-multiple-output.xsl");
		optionsParams.put("output", "/transformed/multiple");
		optionsParams.put("overwrite", "");
		optionsParams.put("comment", "Automatic transform!");
		configOptions.setParams(optionsParams);

		config.setOptions(configOptions);

		RepoRevision rev = null;
		try {
			rev = transformService.transform(item, config);
		} catch (IllegalArgumentException e) {
			fail("Output folder is valid. Should not throw excepetion: " + e.getMessage());
		}

		CmsItemId itemIdNew = new CmsItemIdArg(repo, new CmsItemPath(optionsParams.get("output")).append(itemId.getRelPath().getName()));
		CmsItem itemNew = lookup.getItem(itemIdNew);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		itemNew.getContents(baos);

		String string = baos.toString(StandardCharsets.UTF_8.name());
		assertTrue(string.contains("multiple-output=\"true\""));

	}

	@Test
	public void testStylesheetCmsBuiltIn() throws Exception {
		// stylesheet param is filename.
	}

	@Test
	@Ignore
	public void testCommentIsVelocityString() throws Exception {
		// Future implementation. Comment may be a velocity formated string.
	}


	@Test
	public void testInvalidConfig() throws Exception {
		// Invalid config
		// No config
	}

}
