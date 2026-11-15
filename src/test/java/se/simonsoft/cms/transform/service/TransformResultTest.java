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

import java.util.Date;
import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;

public class TransformResultTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private static CmsItemLock getLock(String owner, String comment) {
		return new CmsItemLock() {
			@Override public String getComment() { return comment; }
			@Override public Date getDateCreation() { return new Date(0); }
			@Override public Date getDateExpiration() { return null; }
			@Override public String getToken() { return "opaquelocktoken:" + getTokenUUID(); }
			@Override public UUID getTokenUUID() { return new UUID(0, 1); }
			@Override public String getOwner() { return owner; }
			@Override public CmsItemId getItemId() { return null; }
		};
	}

	@Test
	public void testJson() throws Exception {
		TransformResult result = new TransformResult(true, new RepoRevision(12, null));
		result.addAdded(new CmsItemPath("/doc/sections/section1.xml"));
		result.addModified(new CmsItemPath("/doc/transform-test.xml"));
		result.addLocked(new CmsItemPath("/doc/transform-test.xml"), getLock("editor", "Editing"));

		JsonNode json = mapper.readTree(result.toJson(mapper.writer(), TransformResult.JSON_MAX_LENGTH));
		assertEquals("Dry run, nothing committed: 1 items added, 1 items modified, 1 items locked", json.get("message").asText());
		assertTrue(json.get("dryRun").asBoolean());
		assertEquals(12, json.get("baseRevision").asLong());
		assertFalse(json.has("revision"));
		assertEquals("/doc/sections/section1.xml", json.get("added").get(0).asText());
		assertEquals("/doc/transform-test.xml", json.get("modified").get(0).asText());
		JsonNode lock = json.get("locked").get(0);
		assertEquals("/doc/transform-test.xml", lock.get("path").asText());
		assertEquals("editor", lock.get("owner").asText());
		assertEquals("Editing", lock.get("comment").asText());
		assertEquals("1970-01-01T00:00:00Z", lock.get("created").asText());
		assertEquals(0, json.get("addedOmitted").asInt());
	}

	@Test
	public void testJsonMaxLength() throws Exception {
		TransformResult result = new TransformResult(true, new RepoRevision(12, null));
		for (int i = 0; i < 1000; i++) {
			result.addAdded(new CmsItemPath(String.format("/doc/sections/section%04d.xml", i)));
			result.addModified(new CmsItemPath(String.format("/doc/topics/topic%04d.xml", i)));
			result.addLocked(new CmsItemPath(String.format("/doc/topics/topic%04d.xml", i)), getLock("editor", "Editing"));
		}

		int maxLength = 20000;
		String s = result.toJson(mapper.writer(), maxLength);
		assertTrue(s.length() <= maxLength);
		JsonNode json = mapper.readTree(s);
		assertEquals(1000, json.get("lockedCount").asInt());
		// Locks first, then modified and added within the remaining length.
		int locked = json.get("locked").size();
		assertTrue(locked > 0);
		assertEquals(1000 - locked, json.get("lockedOmitted").asInt());
		assertEquals(1000 - json.get("modified").size(), json.get("modifiedOmitted").asInt());
		assertEquals(1000 - json.get("added").size(), json.get("addedOmitted").asInt());
		assertEquals(0, json.get("modified").size());
		assertEquals(1000, json.get("addedOmitted").asInt());
	}
}
