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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationException;

/**
 * A dry run (param dryrun) found that the transform would fail: schema validation issues and / or existing locks
 * on items that would be modified. Nothing is locked or committed.
 */
public class TransformDryRunException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Error name of the command failure when items are locked but schema validation passed (or was not requested).
	 * With validation issues the error name is {@link TransformSchemaValidationException#ERROR_NAME}, same as without dryrun.
	 */
	public static final String ERROR_NAME_LOCKED = "TransformLocked";

	// Room for lockedCount, locked and lockedOmitted when the validation issues fill the JSON.
	private static final int JSON_LOCKED_MIN_LENGTH = 200;

	private final TransformSchemaValidationException validation;
	private final Map<CmsItemPath, CmsItemLock> locked;

	/**
	 * @param validation the schema validation failure, null if validation passed or was not requested
	 * @param locked existing locks on items that would be modified, possibly empty
	 */
	public TransformDryRunException(TransformSchemaValidationException validation, Map<CmsItemPath, CmsItemLock> locked) {
		super(getMessage(validation, locked), validation);
		this.validation = validation;
		this.locked = Collections.unmodifiableMap(new LinkedHashMap<>(locked));
	}

	private static String getMessage(TransformSchemaValidationException validation, Map<CmsItemPath, CmsItemLock> locked) {
		String lockedMessage = String.format("%d items locked", locked.size());
		return validation != null ? "Dry run, " + validation.getMessage() + ", " + lockedMessage : "Dry run failed: " + lockedMessage;
	}

	/**
	 * @return the schema validation failure, null if validation passed or was not requested
	 */
	public TransformSchemaValidationException getValidation() {
		return validation;
	}

	/**
	 * @return existing locks on items that would be modified
	 */
	public Map<CmsItemPath, CmsItemLock> getLocked() {
		return locked;
	}

	/**
	 * @return error name of the command failure, e.g. the Step Functions task failure
	 */
	public String getErrorName() {
		return validation != null ? TransformSchemaValidationException.ERROR_NAME : ERROR_NAME_LOCKED;
	}

	/**
	 * The validation issues have priority, then the locks that fit within maxLength (UTF-8 bytes).
	 * The 'lockedCount' is always included, the remaining locks are counted in 'lockedOmitted'.
	 * No locks are listed if any validation issue is omitted.
	 * @return JSON with the message, the validation counts and issues (if any) and the locks (path, owner, comment, created)
	 */
	public String toJson(ObjectWriter writer, int maxLength) {
		ObjectNode result = validation != null ? validation.toJsonNode(writer, maxLength - JSON_LOCKED_MIN_LENGTH) : JsonNodeFactory.instance.objectNode();
		result.put("message", getMessage()); // Replaces the validation message, keeping its position.
		result.put("dryRun", true);
		result.put("lockedCount", locked.size());
		ArrayNode array = result.putArray("locked");
		result.put("lockedOmitted", locked.size()); // At least as long as the final value.

		// Only the count of the locks when the validation issues do not fit.
		if (validation == null || result.path("issuesOmitted").asInt() == 0) {
			int length = getLength(writer, result);
			for (Map.Entry<CmsItemPath, CmsItemLock> e : locked.entrySet()) {
				ObjectNode node = JsonNodeFactory.instance.objectNode();
				node.put("path", e.getKey().getPath());
				node.put("owner", e.getValue().getOwner());
				node.put("comment", e.getValue().getComment());
				if (e.getValue().getDateCreation() != null) {
					node.put("created", e.getValue().getDateCreation().toInstant().toString());
				}
				int nodeLength = getLength(writer, node) + 1; // Separator.
				if (length + nodeLength > maxLength) {
					break;
				}
				array.add(node);
				length += nodeLength;
			}
		}
		result.put("lockedOmitted", locked.size() - array.size());
		return toString(writer, result);
	}

	private static int getLength(ObjectWriter writer, ObjectNode node) {
		return toString(writer, node).getBytes(StandardCharsets.UTF_8).length;
	}

	private static String toString(ObjectWriter writer, ObjectNode node) {
		try {
			return writer.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize dry run failure.", e);
		}
	}
}
