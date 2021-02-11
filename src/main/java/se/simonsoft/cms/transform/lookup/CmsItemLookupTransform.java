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

import java.util.Arrays;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.info.CmsConnectionException;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.reporting.response.CmsItemReporting;

public class CmsItemLookupTransform implements CmsItemLookup {
	
	private static final Logger logger = LoggerFactory.getLogger(CmsItemLookupTransform.class);
	
	private static String CMS_CLASS_PROPERTY = "cms:class";
	private static String CLASS = "tikahtml";
	private static String FIELD = "tf_tikahtml";
	private final CmsItemLookup itemLookup;
	private final CmsItemLookupReporting itemLookupReporting;

	public CmsItemLookupTransform(CmsItemLookup itemLookup, CmsItemLookupReporting itemLookupReporting) {
		this.itemLookup = itemLookup;
		this.itemLookupReporting = itemLookupReporting;
	}
	
	@Override
	public CmsItem getItem(CmsItemId id) throws CmsConnectionException, CmsItemNotFoundException {
		
		CmsItem item = this.itemLookup.getItem(id);
		CmsItemProperties properties = item.getProperties();
		
		if (!hasClass(properties)) {
			//If it is not a keydefmap we just return item as is.
			logger.trace("Not an extracted Keydefmap: {}", id);
			return item;
		}
		
		logger.debug("Resolving extracted Keydefmap: {}", id);
		CmsItemReporting reportingItem = (CmsItemReporting) this.itemLookupReporting.getItem(id);
		return new CmsItemTransform(reportingItem);
	}
	
	private boolean hasClass(CmsItemProperties properties) {

		if (properties == null) {
			return false;
		}

		String classes = properties.getString(CMS_CLASS_PROPERTY);
		if (classes == null || classes.isEmpty()) {
			return false;
		}
		
		String[] a = classes.split(" ");
		return Arrays.asList(a).contains(CLASS);
	}
	
	@Override
	public Set<CmsItemId> getImmediateFolders(CmsItemId parent) throws CmsConnectionException, CmsItemNotFoundException {
		return this.itemLookup.getImmediateFolders(parent);
	}

	@Override
	public Set<CmsItemId> getImmediateFiles(CmsItemId parent) throws CmsConnectionException, CmsItemNotFoundException {
		return this.itemLookup.getImmediateFiles(parent);
	}

	@Override
	public Set<CmsItem> getImmediates(CmsItemId parent) throws CmsConnectionException, CmsItemNotFoundException {
		return this.itemLookup.getImmediates(parent);
	}

	@Override
	public Iterable<CmsItemId> getDescendants(CmsItemId parent) {
		return this.itemLookup.getDescendants(parent);
	}

	@Override
	public CmsItemLock getLocked(CmsItemId itemId) {
		return this.itemLookup.getLocked(itemId);
	}


}
