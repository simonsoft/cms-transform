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
package se.simonsoft.cms.transform.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import se.repos.testing.indexing.ReposTestIndexing;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.TransformConfigurationDefault;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.transform.testconfig.TestFileXmlSetUp;

public class TransformItemChangedEventListenerTest {
	
	private static ReposTestIndexing indexing = null;

	private static CmsItemLookup lookup;

	static final String hostname = "cmshostname:123"; //Port number has at times been a source of confusion when there are multiple issues at play.

	static CmsRepository repo;

	private static TestFileXmlSetUp testSetUp;

	private static CmsRepositoryLookup repoLookup;
	private static TransformConfiguration transformConfig;
	private Map<CmsRepository, CmsItemLookup> lookups = new HashMap<>();

	static Long startRev = new Long(1); // Defined as youngest in the filexml repo.

	static final String transformTestDoc = "x-svn:///svn/repo1/doc/transform-test.xml";


	@Before
	public void setup() throws Exception {
		testSetUp = new TestFileXmlSetUp(new CmsRepository("http://" + hostname + "/svn/repo1"),
				new FilexmlSourceClasspath("se/simonsoft/cms/transform/datasets/repo1"));
		repo = testSetUp.getRepo();
		indexing = testSetUp.getIndexing();
		lookup = indexing.getContext().getInstance(CmsItemLookup.class);
		repoLookup = indexing.getContext().getInstance(CmsRepositoryLookup.class);
		transformConfig = new TransformConfigurationDefault(repoLookup, new ObjectMapper().reader());
		
		lookups.put(repo, lookup);
	}

	@After
	public void tearDown() {
		indexing.tearDown();
	}
	
	
	@Test
	public void testEventListenerGetsConfigs() {
		
		TransformService spyTransformService = spy(TransformService.class);
		Map<CmsRepository, TransformService> transformServices = new HashMap<CmsRepository, TransformService>();
		transformServices.put(repo, spyTransformService);
		
		TransformConfiguration spyTransformConfig = spy(transformConfig);
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc).withPegRev(1L);
		CmsItem item = lookup.getItem(itemId);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, transformServices, lookups);
		eventListener.onItemChange(item);
		
		ArgumentCaptor<CmsItemId> itemIdCaptor = ArgumentCaptor.forClass(CmsItemId.class);
		verify(spyTransformConfig, times(1)).getConfiguration(itemIdCaptor.capture());
		
		CmsItemId value = itemIdCaptor.getValue();
		assertEquals(itemId, value);
		
		ArgumentCaptor<TransformConfig> argCaptorConfig = ArgumentCaptor.forClass(TransformConfig.class);
		verify(spyTransformService, times(2)).transform(Mockito.any(CmsItem.class), argCaptorConfig.capture());
		
		List<TransformConfig> allConfigs = argCaptorConfig.getAllValues();
		Collections.sort(allConfigs, new ConfigComparator());
		assertEquals(2, allConfigs.size());
		assertEquals("multiple", allConfigs.get(0).getName());
		assertEquals("single", allConfigs.get(1).getName());
	}
	
	@Test
	public void testFailsIfNoPegrev() {
		
		TransformService spyTransformService = spy(TransformService.class);
		Map<CmsRepository, TransformService> transformServices = new HashMap<CmsRepository, TransformService>();
		transformServices.put(repo, spyTransformService);
		
		TransformConfiguration spyTransformConfig = spy(transformConfig);
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, transformServices, lookups);
		try {
			eventListener.onItemChange(item);
			fail("Should fail");
		} catch (IllegalArgumentException e) {
			assertEquals("Item requires a revision to be transformed.", e.getMessage());
		}
	}
	
	
	@Test
	public void testFolderInputNoTransform() {
		TransformService spyTransformService = spy(TransformService.class);
		Map<CmsRepository, TransformService> transformServices = new HashMap<CmsRepository, TransformService>();
		transformServices.put(repo, spyTransformService);
		
		TransformConfiguration spyTransformConfig = spy(transformConfig);
		
		CmsItemId itemIdFolder = new CmsItemIdArg("x-svn:///svn/repo1/doc").withPegRev(1L);
		CmsItem item = lookup.getItem(itemIdFolder);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, transformServices, lookups);
		eventListener.onItemChange(item);
		
		verify(spyTransformConfig, times(0)).getConfiguration(Mockito.any(CmsItemId.class));
		verify(spyTransformService, times(0)).transform(Mockito.any(CmsItem.class), Mockito.any(TransformConfig.class));
	}
	
	@Test
	public void testItemNotWithinWhitelist() {
		//Repository config white lists /doc and /transformed/single
		
		TransformService spyTransformService = spy(TransformService.class);
		Map<CmsRepository, TransformService> transformServices = new HashMap<CmsRepository, TransformService>();
		transformServices.put(repo, spyTransformService);
		
		TransformConfiguration spyTransformConfig = spy(transformConfig);
		
		CmsItemId itemIdFolder = new CmsItemIdArg("x-svn:///svn/repo1/transformed/multiple/existing/section1.xml").withPegRev(1L);
		CmsItem item = lookup.getItem(itemIdFolder);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, transformServices, lookups);
		eventListener.onItemChange(item);
		
		verify(spyTransformConfig, times(0)).getConfiguration(Mockito.any(CmsItemId.class));
		verify(spyTransformService, times(0)).transform(Mockito.any(CmsItem.class), Mockito.any(TransformConfig.class));
		
	}
	
	
	public class ConfigComparator implements Comparator<TransformConfig> {

		@Override
		public int compare(TransformConfig o1, TransformConfig o2) {
			return o1.getName().compareTo(o2.getName());
		}

	}


}
