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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemLockCollection;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.commit.CmsPatchItem;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.commit.FileAdd;
import se.simonsoft.cms.item.commit.FileModificationLocked;
import se.simonsoft.cms.item.commit.FolderExist;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.properties.CmsItemPropertiesMap;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.lookup.CmsItemLookupTransform;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceDocumentS9api;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.SaxonOutputURIResolverXdm;
import se.simonsoft.cms.xmlsource.transform.TransformOptions;
import se.simonsoft.cms.xmlsource.transform.TransformStreamProvider;
import se.simonsoft.cms.xmlsource.transform.TransformerService;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXsl implements TransformService {
	
	private final CmsCommit commit;
	private final CmsItemLookup itemLookup;
	@SuppressWarnings("unused")
	private final CmsItemLookupReporting itemLookupReporting;
	private final CmsItemLookup itemLookupTransform;
	private final TransformerServiceFactory transformerServiceFactory;
	private final CmsRepositoryLookup repoLookup;
	private final XmlSourceReaderS9api sourceReader;
	private final TransformerService transformerOutput;
	
	private static final String TRANSFORM_LOCK_COMMENT = "Locked for transform";
	private static final String TRANSFORM_BASE_PROP_KEY = "abx:TransformBase";
	private static final String TRANSFORM_NAME_PROP_KEY = "abx:TransformName";
	private static final int HISTORY_MSG_MAX_SIZE = 2000;
	private static final String OUTPUT_TRANSFORM = "se/simonsoft/cms/transform/output.xsl";
  
	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			CmsItemLookupReporting itemLookupReporting,
			CmsRepositoryLookup lookupRepo,
			TransformerServiceFactory transfromerServiceFactory,
			XmlSourceReaderS9api sourceReader
			) {
		
		this.commit = commit;
		this.itemLookup = itemLookup;
		this.itemLookupReporting = itemLookupReporting;
		this.repoLookup = lookupRepo;
		this.transformerServiceFactory = transfromerServiceFactory;
		this.transformerOutput = transfromerServiceFactory.buildTransformerService(new StreamSource(this.getClass().getClassLoader().getResourceAsStream(OUTPUT_TRANSFORM)));
		this.sourceReader = sourceReader;
		
		this.itemLookupTransform = new CmsItemLookupTransform(itemLookup, itemLookupReporting);
	}

	@Override
	public void transform(CmsItemId itemId, TransformConfig config) {
		if (config == null || config.getOptions() == null) {
			throw new IllegalArgumentException("TransformServiceXsl needs a valid TransformConfig object.");
		}
		
		final CmsItemId baseItemId = itemId;
		final CmsRepository repository = baseItemId.getRepository();
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		
		if (!config.getOptions().getType().equals("xsl")) {
			throw new IllegalArgumentException("TransformServiceXsl can only handle xsl transforms but was given: " + config.getOptions().getType());
		}
		
		final String stylesheet = config.getOptions().getParams().get("stylesheet");
		if (stylesheet == null || stylesheet.trim().isEmpty()) {
			throw new IllegalArgumentException("Requires a valid stylesheet path or stylesheet name.");
		}
		
		final CmsItemPath outputPath = getOutputPath(baseItemId ,config.getOptions().getParams().get("output"));
		if (!pathExists(repository, outputPath)) {
			throw new IllegalArgumentException("Specified output must be an existing folder: " + outputPath.getPath());
		}
		
		CmsItem item = itemLookup.getItem(baseItemId);
		
		final TransformerService transformerService = getTransformerService(baseItemId, stylesheet);
		
		// CmsItemLookupTransform will capture items with specific class, normal items will resolve via normal CmsItemLookup.
		transformerService.setItemLookup(itemLookupTransform);
		
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		TransformOptions transformOptions = new TransformOptions();
		
		Set<CmsItemId> items = new LinkedHashSet<>();
		if (item.getKind() == CmsItemKind.Folder) {
			Set<CmsItemId> files = itemLookup.getImmediateFiles(baseItemId);
			// TODO: Consider filtering based on CmsItemClassificationXml in combination with tikahtml cms:class.
			logger.info("Transforming {} items in folder: {}", files.size(), baseItemId);
			items.addAll(files);
		} else {
			items.add(baseItemId);
		}
		
		for (CmsItemId id: items) {
			transformItem(id, config, transformerService, transformOptions, patchset);
		}
		
		List<String> messages = transformOptions.getMessageListener().getMessages();
		String completeMessage = getCompleteMessageString(config.getOptions().getParams().get("comment"), messages);
		if (completeMessage != null && !completeMessage.trim().isEmpty()) {
			patchset.setHistoryMessage(completeMessage);
		}
		
		RepoRevision r = commit.run(patchset);
		logger.debug("Transform complete, commited with rev: {}", r.getNumber());
	}
	
	private void transformItem(CmsItemId baseItemId, TransformConfig config, TransformerService transformerService, TransformOptions transformOptions, CmsPatchset patchset) {
		
		logger.debug("Transforming itemid: {}", baseItemId);
		final CmsItemPropertiesMap props = getProperties(baseItemId, config);
		final CmsItemPath outputPath = getOutputPath(baseItemId, config.getOptions().getParams().get("output"));
		final boolean overwrite = Boolean.valueOf(config.getOptions().getParams().get("overwrite"));
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		TransformStreamProvider baseStreamProvider = transformerService.getTransformStreamProvider(baseItemId, transformOptions);
		addToPatchset(patchset, outputPath.append(baseItemId.getRelPath().getName()), baseStreamProvider, overwrite, props);
		
		Set<String> resultDocsHrefs = outputURIResolver.getResultDocumentHrefs();
		for (String href: resultDocsHrefs) {
			if (href.startsWith("/")) {
				throw new IllegalArgumentException("Relative href must not start with slash: " + href);
			}
			
			XmlSourceDocumentS9api resultDocument = outputURIResolver.getResultDocument(href);
			TransformStreamProvider streamProvider = transformerOutput.getTransformStreamProvider(resultDocument, null);
			
			String decodedHref = decodeHref(href); // Items will be commited with decoded hrefs.
			CmsItemPath path = outputPath.append(Arrays.asList(decodedHref.split("/")));
			addToPatchset(patchset, path, streamProvider, overwrite, props);
		}
	}
	
	private String getCompleteMessageString(String comment, List<String> messages) {
		StringBuilder sb = new StringBuilder();
		sb.append(comment);
		
		if (!messages.isEmpty()) {
			sb.append("\n");
		}
		
		Iterator<String> iterator = messages.iterator();
		boolean addMoreMessages = iterator.hasNext();
		while (addMoreMessages) {
			String message = iterator.next();
			if (message != null && !message.trim().isEmpty()) {
				if ((sb.length() + message.length()) < HISTORY_MSG_MAX_SIZE) {
					sb.append("\n");
					sb.append(message);
					addMoreMessages = iterator.hasNext();
				} else {
					logger.info("Max history message size ({}) reached truncating", HISTORY_MSG_MAX_SIZE);
					sb.append("\n");
					sb.append("...");
					addMoreMessages = false;
				}
			}
		}
		return sb.toString();
	}
	
	private void addToPatchset(CmsPatchset patchset, CmsItemPath relPath, TransformStreamProvider streamProvider, boolean overwrite, CmsItemPropertiesMap properties) {
		try {
			final InputStream transformStream = getInputStreamNotEmpty(streamProvider.get());
			boolean pathExists = pathExists(patchset.getRepository(), relPath);
			if (!pathExists) {
				addFolderExists(patchset, relPath.getParent());
				logger.debug("No file at path: '{}' will add new file.", relPath);
				FileAdd fileAdd = new FileAdd(relPath, transformStream);
				fileAdd.setPropertyChange(properties);
				patchset.add(fileAdd);
			} else if (overwrite){
				logger.debug("Overwrite is allowed, existing file at path '{}' will be modified.", relPath.getPath());
				CmsItemId itemId = patchset.getRepository().getItemId().withRelPath(relPath);
				CmsItemLockCollection locks = commit.lock(TRANSFORM_LOCK_COMMENT, patchset.getBaseRevision(), itemId.getRelPath());
				if (locks != null && locks.getSingle() == null) {
					throw new IllegalStateException("Unable to retrieve the lock token after locking " + itemId);
				}
				patchset.addLock(locks.getSingle());
				FileModificationLocked fileMod = new FileModificationLocked(relPath, transformStream);
				fileMod.setPropertyChange(properties);
				patchset.add(fileMod);
			} else {
				throw new IllegalStateException("Item already exists, config prohibiting overwrite of existing items.");
			}
			
		} catch (IOException e) {
			throw new RuntimeException("Failed to read stream from transform.", e);
		} catch (EmptyStreamException e) {
			logger.warn("Transform of item at path: '{}'  resulted in empty document, will be discarded.", relPath);
		}
	}
	
	private CmsItemPath getOutputPath(CmsItemId itemId, String output) {
		
		CmsItemPath pathResult;
		
		if (output == null || output.trim().isEmpty()) {
			pathResult = itemId.getRepository().getItemId().withRelPath(itemId.getRelPath().getParent()).getRelPath();
			logger.debug("Output folder is not specified will default to items parent folder.");
		} else {
			pathResult = itemId.getRepository().getItemId().withRelPath(new CmsItemPath(output)).getRelPath();
			logger.debug("Output folder is specified: {}", output);
		}
		
		return pathResult;
	}

	private TransformerService getTransformerService(CmsItemId itemId, String stylesheet) {
		
		TransformerService resultService;
		
		if (stylesheet.startsWith("/")) {
			CmsItemId styleSheetItemId = itemId.getRepository().getItemId().withRelPath(new CmsItemPath(stylesheet));
			logger.debug("Using stylesheet from repo: {}", styleSheetItemId.getLogicalId());
			CmsItem styleSheetItem;
			try {
				 styleSheetItem = itemLookup.getItem(styleSheetItemId);
				 logger.debug("Using stylesheet from repo: {} {}", styleSheetItemId.getLogicalId(), styleSheetItem.getRevisionChanged());
			} catch (CmsItemNotFoundException e) {
				throw new IllegalArgumentException("Specified stylesheet does not exist at path: " + stylesheet, e);
			}
			
			// #1367 Now using a stylesheet factory method that provides caching without reading the content.
			resultService = transformerServiceFactory.buildTransformerService(styleSheetItem);
		} else {
			// TODO: Guard against very long string 'stylesheet'.
			// Use named built-in stylesheet.
			resultService = transformerServiceFactory.buildTransformerService(stylesheet);
		}
		
		return resultService;
	}
	
	private boolean pathExists(CmsRepository repo, CmsItemPath path) {
		
		CmsItemId itemIdOutput = repo.getItemId().withRelPath(path);
		boolean result = false;
		
		try {
			itemLookup.getItem(itemIdOutput);
			result = true;
		} catch (CmsItemNotFoundException e) {
			result = false;
		}
		
		return result;
	}
	
	private InputStream getInputStreamNotEmpty(InputStream inputStream) throws IOException, EmptyStreamException {
		int maxRead = 200;
		PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, maxRead);
		byte[] bytes = new byte[maxRead];
		int len = pushbackInputStream.read(bytes, 0, maxRead);
		String data = new String(bytes, StandardCharsets.UTF_8);
		
		if (len == -1 || emptyExceptDeclaration(data)) { 
			throw new EmptyStreamException("Transform is empty");
		}
		
		byte[] copyOf = Arrays.copyOf(bytes, len); // Do not want to pushback 200 bytes if content is less.	
		pushbackInputStream.unread(copyOf);
		return pushbackInputStream;
	}

	private boolean emptyExceptDeclaration(String data) {
		return data.substring(data.indexOf("?>") + 2, data.length()).trim().isEmpty();
	}
	
	private CmsItemPropertiesMap getProperties(CmsItemId baseId, TransformConfig config) {
		
		CmsItemPropertiesMap m = new CmsItemPropertiesMap();
		
		// TODO: Include rev if configured to do so.
		baseId = baseId.withPegRev(null); // Remove revision to avoid commit on items that have not changed.
		
		m.put(TRANSFORM_BASE_PROP_KEY, baseId.getLogicalId());
		m.put(TRANSFORM_NAME_PROP_KEY, config.getName());
		return m;
	}
	
	private void addFolderExists(CmsPatchset patchset, CmsItemPath parentPath) {
		Iterator<CmsPatchItem> iterator = patchset.iterator();
		boolean addFolderExist = true;
		while (iterator.hasNext() && addFolderExist) {
			CmsPatchItem next = iterator.next();
			if (next instanceof FolderExist && next.getPath().compareTo(parentPath) == 0) {
				addFolderExist = false;
			}
		}

		if (addFolderExist) {
			patchset.add(new FolderExist(parentPath));

		}
	}
	
	private String decodeHref(String href) {
		try {
			href = java.net.URLDecoder.decode(href, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			logger.error("Could not decode URL", e);
			throw new IllegalArgumentException("Could not decode URL: " + href);
		}
		return href;
	}
	
	private class EmptyStreamException extends Exception {

		private static final long serialVersionUID = 1L;
		
		public EmptyStreamException(String message) {
			super(message);
		}
		
	}

}
