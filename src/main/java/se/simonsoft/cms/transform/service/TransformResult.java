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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.RepoRevision;

/**
 * The items of one transform, added and modified.
 */
public class TransformResult {

	/**
	 * Same limit as the schema validation failure cause, well below the Step Functions payload limit (256 KiB).
	 */
	public static final int JSON_MAX_LENGTH = 32000;

	private final boolean dryRun;
	private final RepoRevision baseRevision;
	private RepoRevision revision = null;
	private final List<CmsItemPath> added = new ArrayList<>();
	private final List<CmsItemPath> modified = new ArrayList<>();
	private final Map<CmsItemPath, CmsItemLock> locked = new LinkedHashMap<>();

	public TransformResult(boolean dryRun, RepoRevision baseRevision) {
		this.dryRun = dryRun;
		this.baseRevision = baseRevision;
	}

	void addAdded(CmsItemPath path) {
		added.add(path);
	}

	void addModified(CmsItemPath path) {
		modified.add(path);
	}

	void addLocked(CmsItemPath path, CmsItemLock lock) {
		locked.put(path, lock);
	}

	void setRevision(RepoRevision revision) {
		this.revision = revision;
	}

	/**
	 * @return true if nothing was committed (param dryrun)
	 */
	public boolean isDryRun() {
		return dryRun;
	}

	public RepoRevision getBaseRevision() {
		return baseRevision;
	}

	/**
	 * @return the committed revision, null in a dry run
	 */
	public RepoRevision getRevision() {
		return revision;
	}

	public List<CmsItemPath> getAdded() {
		return Collections.unmodifiableList(added);
	}

	public List<CmsItemPath> getModified() {
		return Collections.unmodifiableList(modified);
	}

	/**
	 * Only in a dry run, collected for {@link TransformDryRunException}. The transform takes the locks instead.
	 * @return existing locks on the items that would be modified
	 */
	Map<CmsItemPath, CmsItemLock> getLocked() {
		return Collections.unmodifiableMap(locked);
	}

	public String getMessage() {
		return String.format("%s: %d items added, %d items modified",
				dryRun ? "Dry run, nothing committed" : "Transform committed", added.size(), modified.size());
	}

	/**
	 * The modified paths first, then the added paths, as many as fit within maxLength (UTF-8 bytes).
	 * The remaining are counted in 'modifiedOmitted' and 'addedOmitted', the added paths are omitted if the modified paths are truncated.
	 * @return JSON with the message, base revision, revision (unless dry run), counts and paths
	 */
	public String toJson(ObjectWriter writer, int maxLength) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("message", getMessage());
		result.put("dryrun", dryRun);
		result.put("baseRevision", baseRevision.getNumber());
		if (revision != null) {
			result.put("revision", revision.getNumber());
		}
		result.put("addedCount", added.size());
		result.put("modifiedCount", modified.size());
		ArrayNode modifiedArray = result.putArray("modified");
		ArrayNode addedArray = result.putArray("added");
		// At least as long as the final values.
		result.put("modifiedOmitted", modified.size());
		result.put("addedOmitted", added.size());

		int length = getLength(writer, result);
		length = addPaths(writer, modifiedArray, modified, length, maxLength);
		if (modifiedArray.size() == modified.size()) {
			addPaths(writer, addedArray, added, length, maxLength);
		}

		result.put("modifiedOmitted", modified.size() - modifiedArray.size());
		result.put("addedOmitted", added.size() - addedArray.size());
		return toString(writer, result);
	}

	private static int addPaths(ObjectWriter writer, ArrayNode array, List<CmsItemPath> paths, int length, int maxLength) {
		for (CmsItemPath path : paths) {
			int pathLength = toString(writer, JsonNodeFactory.instance.textNode(path.getPath())).getBytes(StandardCharsets.UTF_8).length + 1; // Separator.
			if (length + pathLength > maxLength) {
				break;
			}
			array.add(path.getPath());
			length += pathLength;
		}
		return length;
	}

	private static int getLength(ObjectWriter writer, ObjectNode node) {
		return toString(writer, node).getBytes(StandardCharsets.UTF_8).length;
	}

	private static String toString(ObjectWriter writer, Object node) {
		try {
			return writer.writeValueAsString(node);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize transform result.", e);
		}
	}
}
