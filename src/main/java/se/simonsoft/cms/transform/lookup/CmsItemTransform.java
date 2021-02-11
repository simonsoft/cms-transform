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
package se.simonsoft.cms.transform.lookup;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.impl.CmsItemBase;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.reporting.response.CmsItemReporting;

public class CmsItemTransform extends CmsItemBase {

	private final CmsItem item;

	private static final String xmlDecl = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";

	public static final String FIELD = "tf_tikahtml";
	

	private final Logger logger = LoggerFactory.getLogger(this.getClass());



	public CmsItemTransform(CmsItemReporting item) {
		this.item = item;
	}

	@Override
	public RepoRevision getRevisionChanged() {
		return item.getRevisionChanged();
	}

	@Override
	public String getRevisionChangedAuthor() {
		return item.getRevisionChangedAuthor();
	}

	@Override
	public CmsItemKind getKind() {
		return item.getKind();
	}

	@Override
	public CmsItemProperties getProperties() {
		return item.getProperties();
	}

	@Override
	public long getFilesize() {
		return getContentsAsString().getBytes(StandardCharsets.UTF_8).length;
	}

	@Override
	public void getContents(OutputStream receiver) throws UnsupportedOperationException {
		try {
			receiver.write(getContentsAsString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException("Could not write to given OutputStream, message: " +  e.getMessage());
		}
	}

	
	public String getContentsAsString() {
		

		Map<String, Object> meta = item.getMeta();
		if (!meta.containsKey(FIELD)) {
			logger.error("Missing field {} in indexing: {}", FIELD, getId());
			throw new IllegalStateException("Missing indexing field.");
		}

		String contents = (String) meta.get(FIELD);
		return contents;
	}

}
