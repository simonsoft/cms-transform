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
package se.simonsoft.cms.transform.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationError;

public class TransformSchemaValidationExceptionTest {

	private final ObjectMapper mapper = new ObjectMapper();

	private TransformSchemaValidationException getException(int issueCount) {
		List<TransformSchemaValidationIssue> issues = new ArrayList<>();
		for (int i = 0; i < issueCount; i++) {
			// Jing messages list the expected elements, often several hundred characters.
			String message = "element \"para\" not allowed anywhere; expected element " + "\"p\", ".repeat(100) + "or \"ul\"";
			issues.add(new TransformSchemaValidationIssue(new CmsItemPath("/dita/xml/topics/Topic " + (i / 10) + " åäö.dita"), new SchemaValidationError(SchemaValidationError.Level.ERROR, message, i + 1, 9, "/topic[1]/body[1]/para[" + (i + 1) + "]")));
		}
		return new TransformSchemaValidationException(issueCount / 10 + 1, issueCount / 10 + 1, issues);
	}

	@Test
	public void testToJson() throws Exception {
		TransformSchemaValidationException e = getException(2);
		assertEquals("Schema validation failed: 1 of 1 items, 2 issues", e.getMessage());

		JsonNode json = mapper.readTree(e.toJson(mapper.writer(), TransformSchemaValidationException.JSON_MAX_LENGTH));
		assertEquals("Schema validation failed: 1 of 1 items, 2 issues", json.get("message").asText());
		assertEquals(1, json.get("itemCount").asInt());
		assertEquals(1, json.get("itemFailedCount").asInt());
		assertEquals(2, json.get("issueCount").asInt());
		assertEquals(0, json.get("issuesOmitted").asInt());
		assertEquals(2, json.get("issues").size());
		JsonNode issue = json.get("issues").get(1);
		assertEquals("/dita/xml/topics/Topic 0 åäö.dita", issue.get("path").asText());
		assertEquals("ERROR", issue.get("level").asText());
		assertEquals(2, issue.get("line").asInt());
		assertEquals(9, issue.get("column").asInt());
		assertEquals("/topic[1]/body[1]/para[2]", issue.get("element").asText());
		assertTrue(issue.get("message").asText().startsWith("element \"para\" not allowed anywhere"));
	}

	@Test
	public void testToJsonOmitted() throws Exception {
		TransformSchemaValidationException e = getException(1000);

		String result = e.toJson(mapper.writer(), TransformSchemaValidationException.JSON_MAX_LENGTH);
		int length = result.getBytes(StandardCharsets.UTF_8).length;
		// Filled with issues (each less than 1000 bytes) up to the max length.
		assertTrue("length " + length, length <= TransformSchemaValidationException.JSON_MAX_LENGTH && length > TransformSchemaValidationException.JSON_MAX_LENGTH - 1000);
		JsonNode json = mapper.readTree(result);
		assertEquals(1000, json.get("issueCount").asInt());
		int included = json.get("issues").size();
		assertEquals(1000 - included, json.get("issuesOmitted").asInt());
		assertEquals("in order of validation", 1, json.get("issues").get(0).get("line").asInt());
	}

	@Test
	public void testToJsonNoIssueFits() throws Exception {
		TransformSchemaValidationException e = getException(2);

		JsonNode json = mapper.readTree(e.toJson(mapper.writer(), 200));
		assertEquals(0, json.get("issues").size());
		assertEquals(2, json.get("issuesOmitted").asInt());
	}
}
