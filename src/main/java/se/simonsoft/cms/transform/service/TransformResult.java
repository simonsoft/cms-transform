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
 * The items of one transform, added and modified, and in a dry run the existing locks that would make the commit fail.
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
	 * @return true if nothing was committed (DryRun param)
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
	 * Only in a dry run, the real transform takes the locks and fails if an item is already locked.
	 * @return existing locks on the items that would be modified
	 */
	public Map<CmsItemPath, CmsItemLock> getLocked() {
		return Collections.unmodifiableMap(locked);
	}

	public String getMessage() {
		return String.format("%s: %d items added, %d items modified, %d items locked",
				dryRun ? "Dry run, nothing committed" : "Transform committed", added.size(), modified.size(), locked.size());
	}

	/**
	 * The locks first, then modified and added paths, as many as fit within maxLength (UTF-8 bytes), the lists after a truncated list are omitted.
	 * The remaining are counted in 'lockedOmitted', 'modifiedOmitted' and 'addedOmitted'.
	 * @return JSON with the message, base revision, counts, locks (path, owner, comment, created) and paths
	 */
	public String toJson(ObjectWriter writer, int maxLength) {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		result.put("message", getMessage());
		result.put("dryRun", dryRun);
		result.put("baseRevision", baseRevision.getNumber());
		if (revision != null) {
			result.put("revision", revision.getNumber());
		}
		result.put("addedCount", added.size());
		result.put("modifiedCount", modified.size());
		result.put("lockedCount", locked.size());
		ArrayNode lockedArray = result.putArray("locked");
		ArrayNode modifiedArray = result.putArray("modified");
		ArrayNode addedArray = result.putArray("added");
		// At least as long as the final values.
		result.put("lockedOmitted", locked.size());
		result.put("modifiedOmitted", modified.size());
		result.put("addedOmitted", added.size());

		int length = getLength(writer, result);
		boolean full = false;
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
				full = true;
				break;
			}
			lockedArray.add(node);
			length += nodeLength;
		}
		// Once a list is truncated, the following lists are omitted.
		if (!full) {
			length = addPaths(writer, modifiedArray, modified, length, maxLength);
		}
		if (!full && modifiedArray.size() == modified.size()) {
			addPaths(writer, addedArray, added, length, maxLength);
		}

		result.put("lockedOmitted", locked.size() - lockedArray.size());
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
