package se.simonsoft.cms.transform.config.databind;

import static org.junit.Assert.*;

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
	private Class<TransformImportConfig> transformImportConfigClass;
	
	private final static String standardConfig = "se/simonsoft/cms/transform/config/databind/standardConfig.json";
	///cms-transform/src/test/resources/

	@Before
	public void setUp() {
		transformImportConfigClass = TransformImportConfig.class;
		
		ObjectMapper objectMapper = new ObjectMapper();
		this.reader = objectMapper.reader().forType(transformImportConfigClass);
		this.writer = objectMapper.writer().forType(transformImportConfigClass);
	}
	
	@Test
	public void testDeserializeConfig() throws Exception {
		
		TransformImportConfig config = reader.readValue(getConfigStr(standardConfig));
		
		assertEquals(config.isActive(), true);
		
		TransformImportConfigOptions options = config.getOptions();
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
		
		TransformImportConfig config = new TransformImportConfig();
		config.setActive(true);
		
		TransformImportConfigOptions options = new TransformImportConfigOptions();
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
