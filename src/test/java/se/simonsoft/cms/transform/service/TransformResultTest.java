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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;

public class TransformResultTest {

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	public void testJson() throws Exception {
		TransformResult result = new TransformResult(true, new RepoRevision(12, null));
		result.addAdded(new CmsItemPath("/doc/sections/section1.xml"));
		result.addModified(new CmsItemPath("/doc/transform-test.xml"));

		JsonNode json = mapper.readTree(result.toJson(mapper.writer(), TransformResult.JSON_MAX_LENGTH));
		assertEquals("Dry run, nothing committed: 1 items added, 1 items modified", json.get("message").asText());
		assertTrue(json.get("dryrun").asBoolean());
		assertEquals(12, json.get("baseRevision").asLong());
		assertFalse(json.has("revision"));
		assertEquals("/doc/sections/section1.xml", json.get("added").get(0).asText());
		assertEquals("/doc/transform-test.xml", json.get("modified").get(0).asText());
		assertEquals(0, json.get("addedOmitted").asInt());
		assertEquals(0, json.get("modifiedOmitted").asInt());
	}

	@Test
	public void testJsonMaxLength() throws Exception {
		TransformResult result = new TransformResult(true, new RepoRevision(12, null));
		for (int i = 0; i < 1000; i++) {
			result.addAdded(new CmsItemPath(String.format("/doc/sections/section%04d.xml", i)));
			result.addModified(new CmsItemPath(String.format("/doc/topics/topic%04d.xml", i)));
		}

		int maxLength = 20000;
		String s = result.toJson(mapper.writer(), maxLength);
		assertTrue(s.length() <= maxLength);
		JsonNode json = mapper.readTree(s);
		assertEquals(1000, json.get("modifiedCount").asInt());
		// Modified first, the added are omitted once the modified are truncated.
		int modified = json.get("modified").size();
		assertTrue(modified > 0);
		assertEquals(1000 - modified, json.get("modifiedOmitted").asInt());
		assertEquals(0, json.get("added").size());
		assertEquals(1000, json.get("addedOmitted").asInt());
	}
}
