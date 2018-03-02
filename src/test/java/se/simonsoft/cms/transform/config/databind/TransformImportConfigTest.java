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
package se.simonsoft.cms.transform.config.databind;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;

public class TransformImportConfigTest {
	
	
	private ObjectReader reader;
	private ObjectWriter writer;
	private Class<TransformConfig> transformImportConfigClass;
	
	private final static String standardConfig = "se/simonsoft/cms/transform/config/databind/TransformConfig.json";

	@Before
	public void setUp() {
		transformImportConfigClass = TransformConfig.class;
		
		ObjectMapper objectMapper = new ObjectMapper();
		this.reader = objectMapper.reader().forType(transformImportConfigClass);
		this.writer = objectMapper.writer().forType(transformImportConfigClass);
	}
	
	@Test
	public void testDeserializeConfig() throws Exception {
		
		TransformConfig config = reader.readValue(getConfigStr(standardConfig));
		
		assertEquals(config.isActive(), true);
		
		TransformConfigOptions options = config.getOptions();
		assertNotNull(options);
		assertEquals("xsl", options.getType());
		
		Map<String, String> params = options.getParams();
		assertNotNull(params);
		assertEquals("/dita/xsl/import.xsl", params.get("stylesheet"));
		assertEquals("/dita/import", params.get("output"));
		assertEquals("true", params.get("overwrite"));
		assertEquals("Import an external file.", params.get("comment"));
		
	}
	
	@Test
	public void testSerializeConfig() throws Exception {
		
		TransformConfig config = new TransformConfig();
		config.setActive(true);
		
		TransformConfigOptions options = new TransformConfigOptions();
		options.setType("xsl");
		
		Map<String, String> params = new HashMap<String, String>();
		params.put("stylesheet", "/dita/xsl/import.xsl");
		params.put("output", "/dita/import");
		params.put("overwrite", "true");
		params.put("comments", "import an external file");
		
		options.setParams(params);
		config.setOptions(options);
		
		String configStr = writer.writeValueAsString(config);
		assertTrue(configStr.contains("\"active\":true"));
		assertTrue(configStr.contains("\"options\""));
		assertTrue(configStr.contains("\"type\":\"xsl\""));
		assertTrue(configStr.contains("\"params\""));
		assertTrue(configStr.contains("\"overwrite\":\"true\""));
		assertTrue(configStr.contains("\"stylesheet\":\"/dita/xsl/import.xsl\""));
		assertTrue(configStr.contains("\"output\":\"/dita/import\""));
		assertTrue(configStr.contains("\"comments\":\"import an external file\""));
		
	}
	
	private String getConfigStr(String path) throws FileNotFoundException, IOException {
		InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(path);
		BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
		StringBuilder out = new StringBuilder();
		String line;
		while((line = reader.readLine()) != null) {
			out.append(line);
		}
		reader.close();
		return out.toString();
	}
}
