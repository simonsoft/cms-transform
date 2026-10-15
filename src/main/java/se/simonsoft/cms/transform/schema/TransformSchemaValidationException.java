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

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * One or more of the transformed items failed schema validation, nothing is committed.
 */
public class TransformSchemaValidationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Error name of the command failure, e.g. the Step Functions task failure.
	 */
	public static final String ERROR_NAME = "SchemaValidationFailed";

	/**
	 * Step Functions limits the task failure cause to 32768 characters.
	 */
	public static final int JSON_MAX_LENGTH = 32000;

	private static final ObjectMapper mapper = new ObjectMapper();

	private final int itemCount;
	private final int itemFailedCount;
	private final List<TransformSchemaValidationIssue> issues;

	public TransformSchemaValidationException(int itemCount, int itemFailedCount, List<TransformSchemaValidationIssue> issues) {
		super(String.format("Schema validation failed: %d of %d items, %d issues", itemFailedCount, itemCount, issues.size()));
		this.itemCount = itemCount;
		this.itemFailedCount = itemFailedCount;
		this.issues = List.copyOf(issues);
	}

	/**
	 * @return number of validated items
	 */
	public int getItemCount() {
		return itemCount;
	}

	/**
	 * @return number of items with one or more issues
	 */
	public int getItemFailedCount() {
		return itemFailedCount;
	}

	/**
	 * @return all issues, in the order of validation
	 */
	public List<TransformSchemaValidationIssue> getIssues() {
		return issues;
	}

	/**
	 * The issues that fit within maxLength (UTF-8 bytes), the remaining are counted in 'issuesOmitted'.
	 * @return JSON with the message, counts and issues (path, level, line, column, message)
	 */
	public String toJson(int maxLength) {
		ObjectNode result = mapper.createObjectNode();
		result.put("message", getMessage());
		result.put("itemCount", itemCount);
		result.put("itemFailedCount", itemFailedCount);
		result.put("issueCount", issues.size());
		ArrayNode array = result.putArray("issues");
		result.put("issuesOmitted", issues.size()); // At least as long as the final value.

		int length = getLength(result);
		for (TransformSchemaValidationIssue issue : issues) {
			ObjectNode node = mapper.createObjectNode();
			node.put("path", issue.getPath().getPath());
			node.put("level", issue.getError().getLevel().name());
			node.put("line", issue.getError().getLineNumber());
			node.put("column", issue.getError().getColumnNumber());
			node.put("message", issue.getError().getMessage());
			int nodeLength = getLength(node) + 1; // Separator.
			if (length + nodeLength > maxLength) {
				break;
			}
			array.add(node);
			length += nodeLength;
		}
		result.put("issuesOmitted", issues.size() - array.size());
		return toString(result);
	}

	private static int getLength(ObjectNode node) {
		return toString(node).getBytes(StandardCharsets.UTF_8).length;
	}

	private static String toString(ObjectNode node) {
		try {
			return mapper.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize schema validation issues.", e);
		}
	}
}
