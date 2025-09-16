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
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.helpers.MessageFormatter;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemLock;
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
import se.simonsoft.cms.item.naming.CmsItemNamePattern;
import se.simonsoft.cms.item.naming.CmsItemNaming;
import se.simonsoft.cms.item.naming.CmsItemNamingShard1K;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.item.properties.CmsItemPropertiesMap;
import se.simonsoft.cms.item.structure.CmsItemClassificationXml;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
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

	private static final int HTTP_URL_CONNECTION_READ_TIMEOUT = 60000;  	// 60 seconds
	private static final int HTTP_URL_CONNECTION_CONNECT_TIMEOUT = 30000;  	// 30 seconds
	private static final String HTTP_URL_CONNECTION_USER_AGENT = "cms-transform/1.0";

	private static final String CMS_CLASS_SHARDPARENT = "shardparent";
	private static final String PROPNAME_CONFIG_ITEMNAMEPATTERN = "cmsconfig:ItemNamePattern";

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
		
		// Prevent repository root, could be supported but requires some refactoring below.
		if (baseItemId.getRelPath() == null) {
			throw new IllegalArgumentException("TransformServiceXsl does not support repository root.");
		}
		
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
			// Filtering based on CmsItemClassificationXml in combination with tikahtml cms:class.
			// Workaround for backend returning itemIds with p=-1, remove when fixed in backend. 
			// Never transforms non-head anyway.
			files.stream().filter(fileId -> isTransformable(fileId)).forEach(fileId -> items.add(fileId.withPegRev(null)));
			logger.info("Transforming {} of {} items in folder: {}", items.size(), files.size(), baseItemId);
		} else {
			items.add(baseItemId);
		}
		
		// Locked items can be any items in the repository (any number), not just the input items.
		final Set<CmsItemLock> locked = new HashSet<>();
		try {
			for (CmsItemId id: items) {
				locked.addAll(transformItem(id, config, transformerService, transformOptions, patchset));
			}
		} catch (RuntimeException e) {
			logger.warn("Failed to transform / lock items: {}", e.getMessage(), e);
			// Release all locks taken by previous iterations of the loop.
			unlockItemsFailure(locked);
			throw e;
		}
		
		List<String> messages = transformOptions.getMessageListener().getMessages();
		String completeMessage = getCompleteMessageString(config.getOptions().getParams().get("comment"), messages);
		if (completeMessage != null && !completeMessage.trim().isEmpty()) {
			patchset.setHistoryMessage(completeMessage);
		}
		
		RepoRevision r = commit.run(patchset);
		logger.debug("Transform complete, commited with rev: {}", r.getNumber());
	}

	@Override
	public void importItem(CmsItemId item, TransformImportOptions config) {
		if (config == null) {
			throw new IllegalArgumentException("Import requires a valid TransformImportOptions object.");
		}

		String url = config.getUrl();
		if (url == null || url.trim().isEmpty()) {
			throw new IllegalArgumentException("Import requires a valid URL.");
		}

		final CmsRepository repository = item.getRepository();
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		final boolean overwrite = Boolean.valueOf(config.getParams().get("overwrite"));
		final CmsItemPropertiesMap properties = config.getItemPropertiesMap();

		final Set<CmsItemLock> locked = new HashSet<>();

		try {
			DownloadResult downloadResult = download(url);
			CmsItemLock lock = addToPatchset(patchset, item, downloadResult, overwrite, properties);
			if (lock != null) {
				locked.add(lock);
			}

			String comment = config.getParams().get("comment");
			if (comment != null && !comment.trim().isEmpty()) {
				patchset.setHistoryMessage(comment);
			}

			RepoRevision r = commit.run(patchset);
			logger.info("Import complete from URL: {}, committed with rev: {}", url, r.getNumber());

		} catch (IOException | URISyntaxException e) {
			logger.error("Failed to download content from URL: {}", url, e);
			unlockItemsFailure(locked);
			throw new RuntimeException("Failed to download content from URL: " + url, e);
		} catch (RuntimeException e) {
			logger.warn("Failed to import item: {}", e.getMessage(), e);
			unlockItemsFailure(locked);
			throw e;
		}
	}

	private DownloadResult download(String url) throws IOException, URISyntaxException {
		HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("GET");
		connection.setConnectTimeout(HTTP_URL_CONNECTION_CONNECT_TIMEOUT);
		connection.setReadTimeout(HTTP_URL_CONNECTION_READ_TIMEOUT);
		connection.setInstanceFollowRedirects(true);

		// Set user agent to avoid blocking by some servers
		connection.setRequestProperty("User-Agent", HTTP_URL_CONNECTION_USER_AGENT);

		int responseCode = connection.getResponseCode();
		if (responseCode == HttpURLConnection.HTTP_OK) {
			String contentType = connection.getContentType();
			String extension = getExtensionFromContentType(contentType);
			return new DownloadResult(connection.getInputStream(), contentType, extension);
		} else {
			throw new IOException("HTTP request failed with response code: " + responseCode + " for URL: " + url);
		}
	}

	private String getExtensionFromContentType(String contentType) {
		if (contentType == null) return null;
		String mimeType = contentType.toLowerCase().split(";")[0].trim();
		switch (mimeType) {
			// Images
			case "image/jpeg": return "jpg";
			case "image/png": return "png";
			case "image/gif": return "gif";
			case "image/svg+xml": return "svg";
			case "image/webp": return "webp";
			case "image/bmp": return "bmp";
			case "image/tiff": return "tiff";
			case "image/x-icon": return "ico";

			// Text/Markup
			case "text/xml":
			case "application/xml": return "xml";
			case "application/json": return "json";
			case "text/plain": return "txt";
			case "text/html": return "html";
			case "text/css": return "css";
			case "text/csv": return "csv";

			// Documents
			case "application/pdf": return "pdf";
			case "application/msword": return "doc";
			case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return "docx";
			case "application/vnd.ms-excel": return "xls";
			case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return "xlsx";
			case "application/vnd.ms-powerpoint": return "ppt";
			case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return "pptx";
			case "application/rtf": return "rtf";

			// Archives
			case "application/zip": return "zip";
			case "application/x-rar-compressed": return "rar";
			case "application/x-tar": return "tar";
			case "application/gzip": return "gz";

			// Code/Scripts
			case "application/javascript":
			case "text/javascript": return "js";

			// Audio/Video
			case "audio/mpeg": return "mp3";
			case "audio/wav": return "wav";
			case "video/mp4": return "mp4";
			case "video/mpeg": return "mpeg";
			case "video/quicktime": return "mov";

			default: return null;
		}
	}

	private boolean isTransformable(CmsItemId itemId) {
		
		CmsItemClassificationXml classification = new CmsItemClassificationXml();
		if (classification.isXml(itemId)) {
			return true;
		}
		CmsItem item = this.itemLookup.getItem(itemId);
		// TODO: Use cms-item method when available.
        return isCmsClass(item.getProperties(), "tikahtml");
    }
	
	private Set<CmsItemLock> transformItem(CmsItemId baseItemId, TransformConfig config, TransformerService transformerService, TransformOptions transformOptions, CmsPatchset patchset) {
		
		logger.debug("Transforming itemid: {}", baseItemId);
		final CmsItemPropertiesMap props = getProperties(baseItemId, config);
		final CmsItemPath outputPath = getOutputPath(baseItemId, config.getOptions().getParams().get("output"));
		final boolean overwrite = Boolean.valueOf(config.getOptions().getParams().get("overwrite"));
		final Set<CmsItemLock> locked = new HashSet<>();
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		try {
			
			TransformStreamProvider baseStreamProvider = transformerService.getTransformStreamProvider(baseItemId, transformOptions);
			locked.add(addToPatchset(patchset, outputPath.append(baseItemId.getRelPath().getName()), baseStreamProvider, overwrite, props));
			
			Set<String> resultDocsHrefs = outputURIResolver.getResultDocumentHrefs();
			for (String href: resultDocsHrefs) {
				if (href.startsWith("/")) {
					throw new IllegalArgumentException("Relative href must not start with slash: " + href);
				}
				
				XmlSourceDocumentS9api resultDocument = outputURIResolver.getResultDocument(href);
				TransformStreamProvider streamProvider = transformerOutput.getTransformStreamProvider(resultDocument, null);
				
				String decodedHref = decodeHref(href); // Items will be commited with decoded hrefs.
				CmsItemPath path = outputPath.append(Arrays.asList(decodedHref.split("/")));
				locked.add(addToPatchset(patchset, path, streamProvider, overwrite, props));
			}
		} catch (RuntimeException e) {
			// Unlock locks taken in this invocation of transformItem.
			unlockItemsFailure(locked);
			throw e;
		}
		return locked;
	}
	
	private void unlockItemsFailure(Set<CmsItemLock> locked) {
		logger.info("Transform failed, unlocking {} items.", locked.size());
		try {
			this.commit.unlock(locked.toArray(new CmsItemLock[0]));
		} catch (Exception e) {
			logger.warn("Failed to unlock items after transform / locking failed: {}", e.getMessage(), e);
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
	
	private CmsItemLock addToPatchset(CmsPatchset patchset, CmsItemId itemId, DownloadResult downloadResult, boolean overwrite, CmsItemPropertiesMap properties) {
		boolean pathExists;
		boolean isFolder = false;
		CmsItemLock lock = null;
		CmsItemPath relPath = itemId.getRelPath();
		CmsRepository repository = itemId.getRepository();
		InputStream inputStream = downloadResult.getInputStream();
		CmsItemNaming itemNaming = new CmsItemNamingShard1K(repository, itemLookup);
		// Determine whether the given path exists and if it is a folder
		try {
			CmsItem item = itemLookup.getItem(itemId);
			isFolder = item.getKind().isFolder();
			pathExists = true;
		} catch (CmsItemNotFoundException e) {
			pathExists = false;
		}
		try {
			final InputStream contentStream = getInputStreamNotEmpty(inputStream);
			if (!pathExists) {
				CmsItemPath itemPath = relPath;
				// The path doesn't exist, it could still be either a file or a folder
				isFolder = relPath.getName().endsWith("/") || !relPath.getName().contains(".");
				if (isFolder) {
					// It's a folder
					addFolderExists(patchset, relPath);
					// Auto-name the file
					CmsItem location = itemLookup.getItem(repository.getItemId(relPath, null));
					CmsItemProperties locationProps = location.getProperties();
					if (!isCmsClass(locationProps, CMS_CLASS_SHARDPARENT)) {
						String msg = MessageFormatter.format("Location is not a shardparent: {}", relPath).getMessage();
						logger.error(msg);
						throw new IllegalArgumentException(msg);
					}
					// The cmsconfig name pattern overrides the bursting rule.
					if (!locationProps.containsProperty(PROPNAME_CONFIG_ITEMNAMEPATTERN)) {
						String msg = MessageFormatter.format("Location does not define a name pattern: {}", relPath).getMessage();
						logger.error(msg);
						throw new IllegalArgumentException(msg);
					}
					CmsItemNamePattern namePattern = new CmsItemNamePattern(locationProps.getString(PROPNAME_CONFIG_ITEMNAMEPATTERN));
					itemPath = itemNaming.getItemPath(relPath, namePattern, downloadResult.getExtension());
				} else {
					// It's a file
					addFolderExists(patchset, relPath.getParent());
				}
				logger.debug("No file at path: '{}' will add new file.", itemPath);
				FileAdd fileAdd = new FileAdd(itemPath, contentStream);
				fileAdd.setPropertyChange(properties);
				patchset.add(fileAdd);
			} else if (isFolder) {
				// The path exists and is a folder
				addFolderExists(patchset, relPath);
				// Auto-name the file
				CmsItem location = itemLookup.getItem(repository.getItemId(relPath, null));
				CmsItemProperties locationProps = location.getProperties();
				if (!isCmsClass(locationProps, CMS_CLASS_SHARDPARENT)) {
					String msg = MessageFormatter.format("Location is not a shardparent: {}", relPath).getMessage();
					logger.error(msg);
					throw new IllegalArgumentException(msg);
				}
				// The cmsconfig name pattern overrides the bursting rule.
				if (!locationProps.containsProperty(PROPNAME_CONFIG_ITEMNAMEPATTERN)) {
					String msg = MessageFormatter.format("Location does not define a name pattern: {}", relPath).getMessage();
					logger.error(msg);
					throw new IllegalArgumentException(msg);
				}
				CmsItemNamePattern namePattern = new CmsItemNamePattern(locationProps.getString(PROPNAME_CONFIG_ITEMNAMEPATTERN));
				CmsItemPath itemPath = itemNaming.getItemPath(relPath, namePattern, "jpg");
				logger.debug("No file at path: '{}' will add new file.", itemPath);
				addFolderExists(patchset, itemPath.getParent());
				FileAdd fileAdd = new FileAdd(itemPath, contentStream);
				fileAdd.setPropertyChange(properties);
				patchset.add(fileAdd);
			} else if (overwrite) {
				// The file exists and is, and we are allowed to overwrite
				logger.debug("Overwrite is allowed, existing file at path '{}' will be modified.", relPath.getPath());
				CmsItemLockCollection locks = commit.lock(TRANSFORM_LOCK_COMMENT, patchset.getBaseRevision(), itemId.getRelPath());
				if (locks != null && locks.getSingle() == null) {
					throw new IllegalStateException("Unable to retrieve the lock token after locking " + itemId);
				}
				lock = locks.getSingle();
				patchset.addLock(lock);
				FileModificationLocked fileMod = new FileModificationLocked(relPath, contentStream);
				fileMod.setPropertyChange(properties);
				patchset.add(fileMod);
			} else {
				// The file exists, and we are not allowed to overwrite it
				throw new IllegalStateException("Item already exists, config prohibiting overwrite of existing items.");
			}
		} catch (IOException e) {
			throw new RuntimeException("Failed to read stream from import.", e);
		} catch (EmptyStreamException e) {
			logger.warn("Import of item at path: '{}'  resulted in empty document, will be discarded.", relPath);
		}
		return lock;
	}

	private CmsItemLock addToPatchset(CmsPatchset patchset, CmsItemPath relPath, TransformStreamProvider streamProvider, boolean overwrite, CmsItemPropertiesMap properties) {
		CmsItemLock lock = null;
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
				lock = locks.getSingle();
				patchset.addLock(lock);
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
		return lock;
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
		return data.substring(data.indexOf("?>") + 2).trim().isEmpty();
	}
	
	private CmsItemPropertiesMap getProperties(CmsItemId baseId, TransformConfig config) {
		
		CmsItemPropertiesMap m = new CmsItemPropertiesMap();
		final boolean propertiesSuppress = Boolean.valueOf(config.getOptions().getParams().get("PropertiesSuppress"));
		
		// TODO: Include rev if configured to do so.
		baseId = baseId.withPegRev(null); // Remove revision to avoid commit on items that have not changed.
		
		if (!propertiesSuppress) {
			m.put(TRANSFORM_BASE_PROP_KEY, baseId.getLogicalId());
			m.put(TRANSFORM_NAME_PROP_KEY, config.getName());
		}
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
        href = java.net.URLDecoder.decode(href, StandardCharsets.UTF_8);
        return href;
	}
	
	private static boolean isCmsClass(CmsItemProperties properties, String cmsClass) {

		if (properties == null) {
			return false;
		}

		String classes = properties.getString("cms:class");
		if (classes == null || classes.isEmpty()) {
			return false;
		}
		
		String[] a = classes.split(" ");
		return Arrays.asList(a).contains(cmsClass);
	}
	
	private class EmptyStreamException extends Exception {

		private static final long serialVersionUID = 1L;
		
		public EmptyStreamException(String message) {
			super(message);
		}
		
	}

	private class DownloadResult {
		private final InputStream inputStream;
		private final String contentType;
		private final String extension;

		public DownloadResult(InputStream inputStream, String contentType, String extension) {
			this.inputStream = inputStream;
			this.contentType = contentType;
			this.extension = extension;
		}

		public InputStream getInputStream() { return inputStream; }
		public String getContentType() { return contentType; }
		public String getExtension() { return extension; }
	}
}
