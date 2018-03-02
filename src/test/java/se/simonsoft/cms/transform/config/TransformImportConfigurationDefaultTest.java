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
package se.simonsoft.cms.transform.config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;

import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.config.CmsConfigOption;
import se.simonsoft.cms.item.config.CmsResourceContext;
import se.simonsoft.cms.item.impl.CmsConfigOptionBase;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

public class TransformImportConfigurationDefaultTest {
	
	private CmsItemId itemId = new CmsItemIdArg("x-svn:///svn/demo1^/test/path/some.xml");
	private static final String TRANSFORM_IMPORT_CONFIG = "cmsconfig-transform:import";
	private static final String TRANSFORM_CHANGE_CONFIG = "cmsconfig-transform:change";
	private static final String PUBLISH_CONFIG = "cmsconfig-publish:web";
	private ObjectReader reader;

	@Mock CmsRepositoryLookup repoLookup;

	
	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		reader = new ObjectMapper().reader();
	}
	
	@Test
	public void testReturnListWithConfigs() throws Exception {
		
		HashMap<String, CmsConfigOption> configs = new HashMap<String, CmsConfigOption>();
		configs.put(TRANSFORM_IMPORT_CONFIG, new CmsConfigOptionBase<>(TRANSFORM_IMPORT_CONFIG, getActiveTransformConfig()));
		configs.put(TRANSFORM_CHANGE_CONFIG, new CmsConfigOptionBase<>(TRANSFORM_CHANGE_CONFIG, getInactiveTransformConfig()));
		configs.put(PUBLISH_CONFIG, new CmsConfigOptionBase<>(PUBLISH_CONFIG, getInactiveTransformConfig()));
		
		when(repoLookup.getConfig(any(CmsItemId.class), any(CmsItemKind.class))).thenReturn(new CmsResourceContext(itemId, configs));
		
		TransformConfigurationDefault configurationDefault = new TransformConfigurationDefault(repoLookup, reader);
		Map<String, TransformConfig> configuration = configurationDefault.getConfiguration(itemId);
		assertEquals("Should return 2 valid configs", 2, configuration.size());
		
		TransformConfig importConfig = configuration.get("import");
		assertTrue(importConfig.isActive());
		assertEquals("xsl", importConfig.getOptions().getType());
		assertEquals("/dita/xsl/import.xsl", importConfig.getOptions().getParams().get("stylesheet"));
		
		assertFalse(configuration.get("change").isActive());
		assertNull(configuration.get("web"));
		
	}
	
	@Test
	public void testNullItemId() {
		
		try {
			TransformConfigurationDefault configurationDefault = new TransformConfigurationDefault(repoLookup, reader);
			configurationDefault.getConfiguration(null);
			fail("Shoudl fail, invalid CmsItemId");
		} catch(IllegalArgumentException e) {
			
		}
		
	}
	
	private String getActiveTransformConfig() {
		return "{\n" + 
				"    \"active\": true,\n" + 
				"    \"options\": {\n" + 
				"        \"type\": \"xsl\",\n" + 
				"        \"params\": {\n" + 
				"            \"stylesheet\": \"/dita/xsl/import.xsl\",\n" + 
				"            \"output\": \"/dita/import\",\n" + 
				"            \"overwrite\": true,\n" + 
				"            \"comment\": \"Import an external file.\"\n" + 
				"        }\n" + 
				"    }\n" + 
				"}";
	}
	
	private String getInactiveTransformConfig() {
		return "{\n" + 
				"    \"active\": false,\n" + 
				"    \"options\": {\n" + 
				"        \"type\": \"xsl\",\n" + 
				"        \"params\": {\n" + 
				"            \"stylesheet\": \"/dita/xsl/import.xsl\",\n" + 
				"            \"output\": \"/dita/import\",\n" + 
				"            \"overwrite\": true,\n" + 
				"            \"comment\": \"Import an external file.\"\n" + 
				"        }\n" + 
				"    }\n" + 
				"}";
	}
	
}
