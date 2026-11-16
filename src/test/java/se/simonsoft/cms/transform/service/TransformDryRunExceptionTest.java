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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationException;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationIssue;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationError;

public class TransformDryRunExceptionTest {

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

	private static Map<CmsItemPath, CmsItemLock> getLocked(int count) {
		Map<CmsItemPath, CmsItemLock> locked = new LinkedHashMap<>();
		for (int i = 0; i < count; i++) {
			locked.put(new CmsItemPath(String.format("/doc/topics/topic%04d.xml", i)), getLock("editor", "Editing"));
		}
		return locked;
	}

	private static TransformSchemaValidationException getValidation(int count) {
		List<TransformSchemaValidationIssue> issues = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			SchemaValidationError error = new SchemaValidationError(SchemaValidationError.Level.ERROR, "element \"para\" not allowed anywhere", 33, 16);
			issues.add(new TransformSchemaValidationIssue(new CmsItemPath(String.format("/doc/topics/topic%04d.xml", i)), error));
		}
		return new TransformSchemaValidationException(count, count, issues);
	}

	@Test
	public void testLocked() throws Exception {
		TransformDryRunException e = new TransformDryRunException(null, getLocked(1));
		assertEquals("TransformLocked", e.getErrorName());
		assertEquals("Dry run failed: 1 items locked", e.getMessage());

		JsonNode json = mapper.readTree(e.toJson(mapper.writer(), TransformSchemaValidationException.JSON_MAX_LENGTH));
		assertEquals("Dry run failed: 1 items locked", json.get("message").asText());
		assertTrue(json.get("dryrun").asBoolean());
		assertFalse(json.has("issues"));
		assertEquals(1, json.get("lockedCount").asInt());
		JsonNode lock = json.get("locked").get(0);
		assertEquals("/doc/topics/topic0000.xml", lock.get("path").asText());
		assertEquals("editor", lock.get("owner").asText());
		assertEquals("Editing", lock.get("comment").asText());
		assertEquals("1970-01-01T00:00:00Z", lock.get("created").asText());
		assertEquals(0, json.get("lockedOmitted").asInt());
	}

	@Test
	public void testLockedMaxLength() throws Exception {
		TransformDryRunException e = new TransformDryRunException(null, getLocked(1000));
		int maxLength = 20000;
		String s = e.toJson(mapper.writer(), maxLength);
		assertTrue(s.length() <= maxLength);
		JsonNode json = mapper.readTree(s);
		assertEquals(1000, json.get("lockedCount").asInt());
		int locked = json.get("locked").size();
		assertTrue(locked > 0);
		assertEquals(1000 - locked, json.get("lockedOmitted").asInt());
	}

	@Test
	public void testValidationPriority() throws Exception {
		TransformDryRunException e = new TransformDryRunException(getValidation(1000), getLocked(1000));
		assertEquals("SchemaValidationFailed", e.getErrorName());
		assertEquals("Dry run, Schema validation failed: 1000 of 1000 items, 1000 issues, 1000 items locked", e.getMessage());

		int maxLength = 20000;
		String s = e.toJson(mapper.writer(), maxLength);
		assertTrue(s.length() <= maxLength);
		JsonNode json = mapper.readTree(s);
		assertEquals(e.getMessage(), json.get("message").asText());
		// The issues fill the JSON, the locks are only counted.
		assertTrue(json.get("issues").size() > 0);
		assertEquals(1000 - json.get("issues").size(), json.get("issuesOmitted").asInt());
		assertEquals(1000, json.get("lockedCount").asInt());
		assertEquals(0, json.get("locked").size());
		assertEquals(1000, json.get("lockedOmitted").asInt());
	}

	@Test
	public void testValidationAndLockedWithinLength() throws Exception {
		TransformDryRunException e = new TransformDryRunException(getValidation(2), getLocked(2));
		JsonNode json = mapper.readTree(e.toJson(mapper.writer(), TransformSchemaValidationException.JSON_MAX_LENGTH));
		assertEquals(2, json.get("issues").size());
		assertEquals(0, json.get("issuesOmitted").asInt());
		assertEquals(2, json.get("locked").size());
		assertEquals(0, json.get("lockedOmitted").asInt());
	}
}
